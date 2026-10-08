package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.ReportKind;
import com.pitchmap.trust.domain.ReportType;
import com.pitchmap.trust.domain.SanctionType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemberReportResolveConcurrencyIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;

    @Autowired
    private MemberReportService memberReportService;

    @Autowired
    private MemberReportReviewService reviewService;

    @Autowired
    private MemberReportActionService actionService;

    @Autowired
    private MemberReportDismissService dismissService;

    @Autowired
    private SanctionLiftService sanctionLiftService;

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;
    private long adminId;
    private long targetId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @RepeatedTest(5)
    @DisplayName("[F-21][NFR-10] 같은 신고에 조치와 기각이 동시에 오면 하나만 성공하고 다른 하나는 REPORT_INVALID_STATE이며 감사 로그는 1건이다")
    void onlyOneOfActionAndDismissSucceeds() throws Exception {
        long reportId = inReviewUrgentReport();

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> actionService.act(
                        new MemberReportActionCommand(reportId, adminId, "WARNING", "경고 사유", false, null)),
                () -> dismissService.dismiss(reportId, adminId, null)));

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
        Outcome failed = outcomes.stream()
                .filter(outcome -> !outcome.succeeded())
                .findFirst()
                .orElseThrow();
        assertThat(failed.error())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode().name()).isEqualTo("REPORT_INVALID_STATE"));
        assertThat(count("admin_audit_log WHERE action IN ('REPORT_ACTION', 'REPORT_DISMISS')"))
                .isEqualTo(1);
        assertThat(count("outbox_event WHERE event_type = 'MEMBER_REPORT_RESOLVED'"))
                .isEqualTo(1);
        String status = jdbc.queryForObject("SELECT status FROM member_report WHERE id = ?", String.class, reportId);
        String temporaryStatus = jdbc.queryForObject(
                "SELECT status FROM sanction WHERE report_id = ? AND type = 'TEMPORARY_72H'", String.class, reportId);
        if (status.equals("ACTIONED")) {
            assertThat(count("sanction WHERE type = 'WARNING'")).isEqualTo(1);
            assertThat(temporaryStatus).isEqualTo("ACTIVE");
        } else {
            assertThat(status).isEqualTo("DISMISSED");
            assertThat(count("sanction WHERE type = 'WARNING'")).isZero();
            assertThat(temporaryStatus).isEqualTo("LIFTED");
        }
    }

    @RepeatedTest(5)
    @DisplayName("[F-21][NFR-10] 같은 신고를 동시에 기각하면 하나만 성공하고 감사 로그와 결과 이벤트는 1건이다")
    void onlyOneDismissSucceeds() throws Exception {
        long reportId = inReviewUrgentReport();

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> dismissService.dismiss(reportId, adminId, null),
                () -> dismissService.dismiss(reportId, adminId, null)));

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()))
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.error()).isInstanceOf(BusinessException.class));
        assertThat(count("admin_audit_log WHERE action = 'REPORT_DISMISS'")).isEqualTo(1);
        assertThat(count("outbox_event WHERE event_type = 'MEMBER_REPORT_RESOLVED'"))
                .isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-21][SN-15] 같은 제재를 동시에 해제하면 하나만 성공하고 다른 하나는 SANCTION_INVALID_STATE이며 감사 로그는 1건이다")
    void onlyOneLiftSucceeds() throws Exception {
        long sanctionId = sanctionConfirmService
                .confirm(new SanctionConfirmCommand(targetId, null, SanctionType.PERMANENT, "심각한 위반", adminId))
                .sanctionId();

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> sanctionLiftService.lift(sanctionId, adminId),
                () -> sanctionLiftService.lift(sanctionId, adminId)));

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()))
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.error())
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode().name()).isEqualTo("SANCTION_INVALID_STATE")));
        assertThat(count("admin_audit_log WHERE action = 'SANCTION_LIFT'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, targetId))
                .isEqualTo("UNVERIFIED");
    }

    @RepeatedTest(5)
    @DisplayName("[F-21][SN-05] 신고 기각과 그 임시 정지의 직접 해제가 동시에 와도 데드락 없이 임시 정지는 LIFTED, 회원은 정지가 풀린다")
    void dismissAndLiftOfTemporarySuspensionDoNotDeadlock() throws Exception {
        long reportId = inReviewUrgentReport();
        long temporaryId = jdbc.queryForObject("SELECT id FROM sanction WHERE report_id = ?", Long.class, reportId);

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> dismissService.dismiss(reportId, adminId, null),
                () -> sanctionLiftService.lift(temporaryId, adminId)));

        for (Outcome outcome : outcomes) {
            if (!outcome.succeeded()) {
                assertThat(outcome.error())
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode().name()).isEqualTo("SANCTION_INVALID_STATE"));
            }
        }
        assertThat(outcomes.get(0).succeeded()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM sanction WHERE id = ?", String.class, temporaryId))
                .isEqualTo("LIFTED");
        assertThat(jdbc.queryForObject("SELECT status FROM member_report WHERE id = ?", String.class, reportId))
                .isEqualTo("DISMISSED");
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, targetId))
                .isEqualTo("UNVERIFIED");
    }

    // 성희롱·위협 신고를 접수해 대상을 임시 정지하고, 검토 중 상태까지 진행한다.
    private long inReviewUrgentReport() {
        long reporterId = fixture.saveVerifiedMember(TestSequence.nickname());
        long basecampId = fixture.saveBasecamp("COMPLETED", null);
        fixture.insertMember(basecampId, reporterId, "MEMBER", "ACTIVE");
        fixture.insertMember(basecampId, targetId, "MEMBER", "ACTIVE");
        long reportId = memberReportService.report(
                reporterId,
                new MemberReportCommand(
                        targetId, basecampId, ReportKind.MEMBER, null, ReportType.HARASSMENT_OR_THREAT, "위협했다"));
        reviewService.startReview(reportId, adminId);
        return reportId;
    }

    private int count(String tableAndCondition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private record Outcome(boolean succeeded, Throwable error) {}

    // 작업을 스레드 하나씩 맡겨 동시에 출발시키고, 작업 순서대로 성공 여부와 던져진 예외를 모아 돌려준다.
    private List<Outcome> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    outcomes.add(new Outcome(true, null));
                } catch (ExecutionException e) {
                    outcomes.add(new Outcome(false, e.getCause()));
                } catch (TimeoutException e) {
                    outcomes.add(new Outcome(false, e));
                }
            }
            return outcomes;
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
        }
    }
}
