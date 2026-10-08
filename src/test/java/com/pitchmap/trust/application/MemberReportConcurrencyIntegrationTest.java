package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.ReportKind;
import com.pitchmap.trust.domain.ReportType;
import com.pitchmap.trust.domain.TrustErrorCode;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class MemberReportConcurrencyIntegrationTest {

    @Autowired
    private MemberReportService reportService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName("[SN-03] 같은 신고를 동시에 2번 하면 1건만 저장되고 나머지는 REPORT_DUPLICATED다")
    void sameReportSubmittedConcurrentlyIsSavedOnce() throws Exception {
        // given
        Scenario scenario = scenario();
        MemberReportCommand command = new MemberReportCommand(
                scenario.targetId, scenario.basecampId, ReportKind.MEMBER, null, ReportType.NO_SHOW, "오지 않았다");

        // when
        List<Throwable> failures = runTwice(() -> reportService.report(scenario.reporterId, command));

        // then
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(TrustErrorCode.REPORT_DUPLICATED));
        assertThat(count("member_report")).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[SN-03][SN-05] 같은 성희롱·위협 신고를 동시에 2번 하면 신고와 임시 정지 기록이 1건씩만 남는다")
    void sameUrgentReportCreatesOneSanction() throws Exception {
        // given
        Scenario scenario = scenario();
        MemberReportCommand command = new MemberReportCommand(
                scenario.targetId,
                scenario.basecampId,
                ReportKind.MEMBER,
                null,
                ReportType.HARASSMENT_OR_THREAT,
                "위협했다");

        // when
        List<Throwable> failures = runTwice(() -> reportService.report(scenario.reporterId, command));

        // then
        assertThat(failures).hasSize(1);
        assertThat(count("member_report")).isEqualTo(1);
        assertThat(count("sanction")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, scenario.targetId))
                .isEqualTo("SUSPENDED");
    }

    @RepeatedTest(5)
    @DisplayName("[SN-05][SN-12] 서로 다른 신고자 2명이 같은 대상을 동시에 긴급 신고하면 둘 다 접수되고 임시 정지 기록이 2건 남는다")
    void differentReportersUrgentReportsOnSameTargetBothSucceed() throws Exception {
        // given
        Scenario scenario = scenario();
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        long secondReporterId = fixture.saveVerifiedMember("두번째 신고자");
        fixture.insertMember(scenario.basecampId, secondReporterId, "MEMBER", "ACTIVE");
        MemberReportCommand command = new MemberReportCommand(
                scenario.targetId,
                scenario.basecampId,
                ReportKind.MEMBER,
                null,
                ReportType.HARASSMENT_OR_THREAT,
                "위협했다");
        AtomicInteger turn = new AtomicInteger();
        long[] reporters = {scenario.reporterId, secondReporterId};

        // when
        List<Throwable> failures = runTwice(() -> reportService.report(reporters[turn.getAndIncrement()], command));

        // then
        assertThat(failures).isEmpty();
        assertThat(count("member_report")).isEqualTo(2);
        assertThat(count("sanction")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, scenario.targetId))
                .isEqualTo("SUSPENDED");
    }

    private Scenario scenario() {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        long basecampId = fixture.saveBasecamp("COMPLETED", MutableClock.DEFAULT_INSTANT);
        long targetId = fixture.leaderIdOf(basecampId);
        long reporterId = fixture.saveVerifiedMember("신고자");
        fixture.insertMember(basecampId, reporterId, "MEMBER", "ACTIVE");
        return new Scenario(basecampId, reporterId, targetId);
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private record Scenario(long basecampId, long reporterId, long targetId) {}

    // 같은 작업을 스레드 둘로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> runTwice(Callable<Long> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Long> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                } catch (TimeoutException e) {
                    failures.add(e);
                }
            }
            return failures;
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }
}
