package com.pitchmap.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class IdempotencyExecutorConcurrencyIntegrationTest {

    private static final int THREADS = 10;
    private static final String OPERATION = "POST /api/programs/1/applications";

    @Autowired
    private IdempotencyExecutor executor;

    @Autowired
    private JdbcTemplate jdbc;

    record Result(int sequence) {}

    @RepeatedTest(5)
    @DisplayName("[NFR-03] 같은 회원이 같은 키로 동시에 보내면 action은 한 번만 실행하고 나머지는 IDEMPOTENCY_IN_PROGRESS")
    void actionRunsOnceForSameKey() throws Exception {
        // given
        long memberId = insertMember();
        AtomicInteger calls = new AtomicInteger();
        // 먼저 선점한 요청이 처리 중인 동안 나머지 요청이 모두 들어오도록, 모든 요청이 끝난 것을 알릴 때까지 action을 붙잡아 둔다.
        CountDownLatch allRequestsFinishedOrBlocked = new CountDownLatch(THREADS - 1);

        // when
        List<Object> outcomes = sendConcurrently(
                memberId,
                () -> {
                    int sequence = calls.incrementAndGet();
                    await(allRequestsFinishedOrBlocked);
                    return new Result(sequence);
                },
                allRequestsFinishedOrBlocked);

        // then
        assertThat(calls.get()).isEqualTo(1);
        List<Object> successes =
                outcomes.stream().filter(o -> o instanceof Result).toList();
        List<Object> failures =
                outcomes.stream().filter(o -> !(o instanceof Result)).toList();
        assertThat(successes).hasSize(1).containsOnly(new Result(1));
        assertThat(failures)
                .hasSize(THREADS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM idempotency_record WHERE response_status IS NOT NULL", Integer.class))
                .isEqualTo(1);

        // 끝난 뒤 다시 보내면 저장된 값을 재생한다.
        Result replayed = executor.execute(
                new IdempotentRequest(memberId, "key-1", OPERATION, null),
                HttpStatus.CREATED,
                Result.class,
                () -> new Result(99));
        assertThat(replayed).isEqualTo(new Result(1));
        assertThat(calls.get()).isEqualTo(1);
    }

    private List<Object> sendConcurrently(
            long memberId, Supplier<Result> action, CountDownLatch finishedOrBlockedSignal) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return executor.execute(
                                new IdempotentRequest(memberId, "key-1", OPERATION, null),
                                HttpStatus.CREATED,
                                Result.class,
                                action);
                    } finally {
                        // 성공한 요청의 action 안에서도 같은 래치를 기다리므로, 실패하는 요청만 세어도 THREADS - 1에 닿는다.
                        finishedOrBlockedSignal.countDown();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return collect(futures);
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Object> collect(List<Future<Result>> futures) throws InterruptedException {
        List<Object> outcomes = new ArrayList<>();
        for (Future<Result> future : futures) {
            try {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                outcomes.add(e.getCause());
            } catch (TimeoutException e) {
                outcomes.add(e);
            }
        }
        return outcomes;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("다른 요청이 끝나기를 기다리다 시간이 초과됐습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private long insertMember() {
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO member (email, password_hash, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'UNVERIFIED', 'USER', ?, ?)",
                TestSequence.email(),
                "x".repeat(60),
                TestSequence.nickname(),
                Timestamp.from(now),
                Timestamp.from(now));
        return jdbc.queryForObject("SELECT MAX(id) FROM member", Long.class);
    }
}
