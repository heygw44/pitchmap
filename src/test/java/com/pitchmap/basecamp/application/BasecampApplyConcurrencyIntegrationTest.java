package com.pitchmap.basecamp.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.basecamp.domain.BasecampErrorCode;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
class BasecampApplyConcurrencyIntegrationTest {

    private static final int MAX_PENDING = 20;
    private static final int SAME_MEMBER_THREADS = 5;
    private static final String AT_DEFAULT_INSTANT = "2026-10-05 03:00:00";

    @Autowired
    private BasecampApplyService basecampApplyService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @RepeatedTest(5)
    @DisplayName(
            "[F-13][BC-07] 대기 신청이 19건일 때 서로 다른 회원 2명이 동시에 신청하면 1건만 성공하고 나머지는 BASECAMP_PENDING_LIMIT이며 대기 신청은 20건이다")
    void onlyOneOfTwoConcurrentApplicationsSucceedsAtNinteenPending() throws Exception {
        // given
        long basecampId = saveBasecamp();
        savePendingApplications(basecampId, MAX_PENDING - 1);
        List<Long> applicants = saveVerifiedMembers(2);

        // when
        List<Throwable> failures = runConcurrently(applicants, memberId -> apply(basecampId, memberId));

        // then
        assertThat(failures)
                .hasSize(1)
                .allSatisfy(failure -> assertErrorCode(failure, BasecampErrorCode.BASECAMP_PENDING_LIMIT));
        assertThat(pendingCount(basecampId)).isEqualTo(MAX_PENDING);
        assertThat(outboxEventCount()).isEqualTo(1);
    }

    @RepeatedTest(5)
    @DisplayName("[F-13][BC-07] 대기 신청이 15건일 때 서로 다른 회원 10명이 동시에 신청하면 5건만 성공하고 대기 신청은 20건을 넘지 않는다")
    void pendingNeverExceedsTwentyUnderManyConcurrentApplications() throws Exception {
        // given
        long basecampId = saveBasecamp();
        savePendingApplications(basecampId, MAX_PENDING - 5);
        List<Long> applicants = saveVerifiedMembers(10);

        // when
        List<Throwable> failures = runConcurrently(applicants, memberId -> apply(basecampId, memberId));

        // then
        assertThat(failures)
                .hasSize(5)
                .allSatisfy(failure -> assertErrorCode(failure, BasecampErrorCode.BASECAMP_PENDING_LIMIT));
        assertThat(pendingCount(basecampId)).isEqualTo(MAX_PENDING);
        assertThat(outboxEventCount()).isEqualTo(5);
    }

    @RepeatedTest(5)
    @DisplayName("[F-13][BC-07] 같은 회원이 같은 베이스캠프에 동시에 여러 번 신청하면 1건만 성공하고 나머지는 BASECAMP_ALREADY_APPLIED이며 신청 행은 1개다")
    void sameMemberAppliesOnlyOnce() throws Exception {
        // given
        long basecampId = saveBasecamp();
        long memberId = saveVerifiedMembers(1).get(0);

        // when
        List<Throwable> failures =
                runConcurrently(Collections.nCopies(SAME_MEMBER_THREADS, memberId), id -> apply(basecampId, id));

        // then
        assertThat(failures)
                .hasSize(SAME_MEMBER_THREADS - 1)
                .allSatisfy(failure -> assertErrorCode(failure, BasecampErrorCode.BASECAMP_ALREADY_APPLIED));
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM basecamp_application WHERE basecamp_id = ? AND applicant_id = ?",
                        Integer.class,
                        basecampId,
                        memberId))
                .isEqualTo(1);
        assertThat(outboxEventCount()).isEqualTo(1);
    }

    private Object apply(long basecampId, long memberId) {
        return basecampApplyService.apply(new BasecampApplyCommand(basecampId, memberId, "같이 가요"));
    }

    private static void assertErrorCode(Throwable failure, BasecampErrorCode expected) {
        assertThat(failure)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    // 본인확인을 마친 2007년생 성인 회원을 직접 저장한다. 신뢰 단계가 1이라 합류 신청 자격이 있다.
    private List<Long> saveVerifiedMembers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long memberId = saveMember();
            jdbc.update(
                    "INSERT INTO identity_verification (member_id, birth_year, gender, ci_hash, provider, verified_at,"
                            + " created_at, updated_at) VALUES (?, 2007, 'MALE', ?, 'FAKE', ?, ?, ?)",
                    memberId,
                    "%064d".formatted(memberId),
                    AT_DEFAULT_INSTANT,
                    AT_DEFAULT_INSTANT,
                    AT_DEFAULT_INSTANT);
            ids.add(memberId);
        }
        return ids;
    }

    // 캠프 리더와 그 멤버 행까지 만든 모집 중 베이스캠프다. 정원은 6명이다.
    private long saveBasecamp() {
        long leaderId = saveMember();
        long spotId = saveSpot();
        LocalDate startDate = LocalDate.of(2026, 10, 20);
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '굴업도 주말 1박', '함께 가요', ?, ?, 6, 'RECRUITING', ?, ?)",
                leaderId,
                spotId,
                startDate,
                startDate.plusDays(2),
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        long basecampId = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at, updated_at)"
                        + " VALUES (?, ?, 'LEADER', 'ACTIVE', ?, ?, ?)",
                basecampId,
                leaderId,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT,
                AT_DEFAULT_INSTANT);
        return basecampId;
    }

    private long saveSpot() {
        jdbc.update("INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('BAKJI', '개머리언덕', ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))");
        return jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
    }

    private void savePendingApplications(long basecampId, int count) {
        for (int i = 0; i < count; i++) {
            jdbc.update(
                    "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, created_at, updated_at)"
                            + " VALUES (?, ?, 'PENDING', ?, ?)",
                    basecampId,
                    saveMember(),
                    AT_DEFAULT_INSTANT,
                    AT_DEFAULT_INSTANT);
        }
    }

    private int pendingCount(long basecampId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM basecamp_application WHERE basecamp_id = ? AND status = 'PENDING'",
                Integer.class,
                basecampId);
    }

    private int outboxEventCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'BASECAMP_APPLIED'", Integer.class);
    }

    // 회원마다 스레드 하나로 동시에 출발시키고, 던져진 예외를 모아 돌려준다.
    private List<Throwable> runConcurrently(List<Long> memberIds, MemberAction action) throws Exception {
        int threads = memberIds.size();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (long memberId : memberIds) {
                Callable<Object> task = () -> {
                    ready.countDown();
                    start.await();
                    return action.run(memberId);
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

    @FunctionalInterface
    private interface MemberAction {
        Object run(long memberId) throws Exception;
    }
}
