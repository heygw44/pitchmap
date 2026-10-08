package com.pitchmap.basecamp.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.basecamp.domain.KickReason;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.LocalDate;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BasecampMembershipConcurrencyIntegrationTest {

    private static final String AT_DEFAULT_INSTANT = "2026-10-05 03:00:00";

    @Autowired
    private BasecampMembershipService basecampMembershipService;

    @Autowired
    private BasecampApprovalService basecampApprovalService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName(
            "[F-13][BC-22] 같은 멤버를 강퇴하는 요청과 그 멤버의 탈퇴가 동시에 오면 1건만 성공하고 나머지는 NOT_FOUND이며, 인원은 1명만 줄고 자동 마감은 한 번만 다시 열린다")
    void kickAndLeaveOfSameMemberSucceedOnce() throws Exception {
        // given
        Scenario scenario = saveFullBasecamp();
        List<Callable<Object>> tasks = List.of(
                () -> {
                    basecampMembershipService.kick(
                            scenario.basecampId(), scenario.memberId(), scenario.leaderId(), KickReason.OTHER);
                    return null;
                },
                () -> {
                    basecampMembershipService.leave(scenario.basecampId(), scenario.memberId());
                    return null;
                });

        // when
        List<Throwable> failures = runConcurrently(tasks);

        // then
        assertThat(failures)
                .hasSize(1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND)));
        String memberStatus = jdbc.queryForObject(
                "SELECT status FROM basecamp_member WHERE basecamp_id = ? AND member_id = ?",
                String.class,
                scenario.basecampId(),
                scenario.memberId());
        assertThat(memberStatus).isIn("LEFT", "KICKED");
        assertThat(activeMemberCount(scenario.basecampId())).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT status, closed_reason FROM basecamp WHERE id = ?", scenario.basecampId()))
                .containsEntry("status", "RECRUITING")
                .containsEntry("closed_reason", null);
        assertThat(eventCount("BASECAMP_KICKED")).isEqualTo("KICKED".equals(memberStatus) ? 1 : 0);
    }

    @RepeatedTest(5)
    @DisplayName("[F-13][BC-08] 멤버의 탈퇴와 그 자리를 채우는 승인이 동시에 오면 둘 다 성공하고 ACTIVE 멤버는 정원을 넘지 않으며 모집 중으로 끝난다")
    void leaveAndApprovalIntoFreedSeatBothSucceed() throws Exception {
        // given
        Scenario scenario = saveRecruitingBasecampWithPendingApplication();
        List<Callable<Object>> tasks = List.of(
                () -> {
                    basecampMembershipService.leave(scenario.basecampId(), scenario.memberId());
                    return null;
                },
                () -> basecampApprovalService.approve(
                        scenario.basecampId(), scenario.applicationId(), scenario.leaderId()));

        // when
        List<Throwable> failures = runConcurrently(tasks);

        // then
        assertThat(failures).isEmpty();
        assertThat(activeMemberCount(scenario.basecampId())).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT status, closed_reason FROM basecamp WHERE id = ?", scenario.basecampId()))
                .containsEntry("status", "RECRUITING")
                .containsEntry("closed_reason", null);
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM basecamp_member WHERE basecamp_id = ? AND member_id = ?",
                        String.class,
                        scenario.basecampId(),
                        scenario.memberId()))
                .isEqualTo("LEFT");
    }

    // 캠프 리더와 본인확인한 멤버 2명이 모두 ACTIVE이고 정원 3명이 차서 자동 마감된 베이스캠프다. 첫 멤버가 대상이다.
    private Scenario saveFullBasecamp() {
        long leaderId = saveMember();
        long basecampId = saveBasecamp(leaderId, "CLOSED", "'AUTO_FULL'");
        insertMember(basecampId, leaderId, "LEADER");
        long memberId = saveVerifiedMember();
        insertMember(basecampId, memberId, "MEMBER");
        insertMember(basecampId, saveVerifiedMember(), "MEMBER");
        return new Scenario(basecampId, leaderId, memberId, 0);
    }

    // 캠프 리더와 멤버 한 명이 있는 정원 3명의 모집 중 베이스캠프에 대기 신청 한 건이 있다.
    private Scenario saveRecruitingBasecampWithPendingApplication() {
        long leaderId = saveMember();
        long basecampId = saveBasecamp(leaderId, "RECRUITING", "NULL");
        insertMember(basecampId, leaderId, "LEADER");
        long memberId = saveVerifiedMember();
        insertMember(basecampId, memberId, "MEMBER");
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, created_at, updated_at)"
                        + " VALUES (?, ?, 'APPROVED', ?, ?)",
                basecampId,
                memberId,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, created_at, updated_at)"
                        + " VALUES (?, ?, 'PENDING', ?, ?)",
                basecampId,
                saveVerifiedMember(),
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        long applicationId = jdbc.queryForObject("SELECT MAX(id) FROM basecamp_application", Long.class);
        return new Scenario(basecampId, leaderId, memberId, applicationId);
    }

    private long saveBasecamp(long leaderId, String status, String closedReasonLiteral) {
        jdbc.update("INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('BAKJI', '개머리언덕', ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))");
        long spotId = jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
        LocalDate startDate = LocalDate.of(2026, 10, 20);
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " closed_reason, created_at, updated_at)"
                        + " VALUES (?, ?, '굴업도 주말 1박', '함께 가요', ?, ?, 3, ?, " + closedReasonLiteral + ", ?, ?)",
                leaderId,
                spotId,
                startDate,
                startDate.plusDays(2),
                status,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        return jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    // 본인확인을 마친 2007년생 성인 회원을 직접 저장한다. 신뢰 단계가 1이라 탈퇴할 자격이 있다.
    private long saveVerifiedMember() {
        long memberId = saveMember();
        jdbc.update(
                "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,"
                        + " created_at, updated_at) VALUES (?, 2007, 'MALE', ?, 'FAKE', ?, ?, ?)",
                memberId,
                "%064d".formatted(memberId),
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        return memberId;
    }

    private void insertMember(long basecampId, long memberId, String role) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)",
                basecampId,
                memberId,
                role,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
    }

    private int activeMemberCount(long basecampId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM basecamp_member WHERE basecamp_id = ? AND status = 'ACTIVE'",
                Integer.class,
                basecampId);
    }

    private int eventCount(String eventType) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_type = ?", Integer.class, eventType);
    }

    // 작업마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> runConcurrently(List<Callable<Object>> actions) throws Exception {
        int threads = actions.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> action : actions) {
                Callable<Object> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collectFailures(futures);
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Throwable> collectFailures(List<Future<Object>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (TimeoutException e) {
                failures.add(e);
            }
        }
        return failures;
    }

    private record Scenario(long basecampId, long leaderId, long memberId, long applicationId) {}
}
