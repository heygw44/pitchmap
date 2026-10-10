package com.pitchmap.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class IdempotencyExecutorIntegrationTest {

    private static final String OPERATION = "POST /api/programs/1/applications";

    @Autowired
    private IdempotencyExecutor executor;

    @Autowired
    private IdempotencyRecordJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    record Result(long id, String name) {}

    @Test
    @DisplayName("[NFR-03] 첫 요청은 실행해 완료로 저장하고, 같은 키로 다시 보내면 action 없이 같은 값을 돌려준다")
    void replaysStoredResult() {
        long memberId = insertMember();
        AtomicInteger calls = new AtomicInteger();

        Result first = run(memberId, "key-1", OPERATION, Map.of("a", 1), () -> {
            calls.incrementAndGet();
            return new Result(7, "신청");
        });
        Result second = run(memberId, "key-1", OPERATION, Map.of("a", 1), () -> {
            calls.incrementAndGet();
            return new Result(8, "다른 값");
        });

        assertThat(calls.get()).isEqualTo(1);
        assertThat(first).isEqualTo(new Result(7, "신청"));
        assertThat(second).isEqualTo(first);
        IdempotencyRecord stored =
                repository.findById(new IdempotencyRecordId(memberId, "key-1")).orElseThrow();
        assertThat(stored.isCompleted()).isTrue();
        assertThat(stored.getResponseStatus()).isEqualTo((short) 201);
    }

    @Test
    @DisplayName("[NFR-03] 같은 키에 본문이 다르면 IDEMPOTENCY_KEY_REUSED")
    void differentPayloadWithSameKeyIsRejected() {
        long memberId = insertMember();
        run(memberId, "key-1", OPERATION, Map.of("a", 1), () -> new Result(1, "x"));

        assertThatThrownBy(() -> run(memberId, "key-1", OPERATION, Map.of("a", 2), () -> new Result(2, "y")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    @DisplayName("[NFR-03] 같은 키에 operation이 다르면 IDEMPOTENCY_KEY_REUSED")
    void differentOperationWithSameKeyIsRejected() {
        long memberId = insertMember();
        run(memberId, "key-1", OPERATION, null, () -> new Result(1, "x"));

        assertThatThrownBy(() -> run(memberId, "key-1", "POST /api/payments/1", null, () -> new Result(2, "y")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    @DisplayName("[NFR-03] 회원이 다르면 같은 키도 따로 실행한다")
    void sameKeyOfDifferentMembersRunsSeparately() {
        long member1 = insertMember();
        long member2 = insertMember();
        AtomicInteger calls = new AtomicInteger();

        run(member1, "key-1", OPERATION, null, () -> new Result(calls.incrementAndGet(), "a"));
        Result second = run(member2, "key-1", OPERATION, null, () -> new Result(calls.incrementAndGet(), "b"));

        assertThat(calls.get()).isEqualTo(2);
        assertThat(second).isEqualTo(new Result(2, "b"));
    }

    @Test
    @DisplayName("[NFR-03] 응답 없는 선점 행이 있으면 IDEMPOTENCY_IN_PROGRESS이고 action을 부르지 않는다")
    void inProgressRowIsConflict() {
        long memberId = insertMember();
        // 같은 요청의 해시를 그대로 두려고 실행기로 먼저 한 번 처리한 뒤, 응답만 비워 처리 중인 상태로 만든다.
        run(memberId, "key-1", OPERATION, null, () -> new Result(1, "x"));
        jdbc.update("UPDATE idempotency_record SET response_status = NULL, response_body = NULL");
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> run(memberId, "key-1", OPERATION, null, () -> {
                    calls.incrementAndGet();
                    return new Result(2, "y");
                }))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS));
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("[NFR-03] action이 BusinessException을 던지면 선점 행을 지우고, 같은 키로 다시 보내면 다시 실행한다")
    void failedActionReleasesClaim() {
        long memberId = insertMember();
        BusinessException failure = new BusinessException(CommonErrorCode.ACCESS_DENIED);

        assertThatThrownBy(() -> run(memberId, "key-1", OPERATION, null, () -> {
                    throw failure;
                }))
                .isSameAs(failure);
        assertThat(count()).isZero();

        Result retried = run(memberId, "key-1", OPERATION, null, () -> new Result(3, "재시도"));

        assertThat(retried).isEqualTo(new Result(3, "재시도"));
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("[NFR-03] 키가 없으면 IDEMPOTENCY_KEY_REQUIRED, 형식이 틀리면 INVALID_INPUT")
    void rejectsMissingAndMalformedKey() {
        long memberId = insertMember();

        assertThatThrownBy(() -> run(memberId, null, OPERATION, null, () -> new Result(1, "x")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED));
        assertThatThrownBy(() -> run(memberId, "bad key", OPERATION, null, () -> new Result(1, "x")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("[NFR-03] 대소문자만 다른 키는 같은 키로 본다")
    void keysDifferingOnlyByCaseAreTheSame() {
        long memberId = insertMember();
        AtomicInteger calls = new AtomicInteger();

        run(memberId, "Key-A", OPERATION, null, () -> new Result(calls.incrementAndGet(), "x"));
        Result second = run(memberId, "key-a", OPERATION, null, () -> new Result(calls.incrementAndGet(), "y"));

        assertThat(calls.get()).isEqualTo(1);
        assertThat(second).isEqualTo(new Result(1, "x"));
    }

    @Test
    @DisplayName("[NFR-03] 트랜잭션 안에서 부르면 IllegalStateException")
    void rejectsCallInsideTransaction() {
        long memberId = insertMember();

        assertThatThrownBy(() -> transactionTemplate.execute(
                        status -> run(memberId, "key-1", OPERATION, null, () -> new Result(1, "x"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count()).isZero();
    }

    private Result run(long memberId, String key, String operation, Object payload, Supplier<Result> action) {
        return executor.execute(
                new IdempotentRequest(memberId, key, operation, payload), HttpStatus.CREATED, Result.class, action);
    }

    private long insertMember() {
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        jdbc.update(
                "INSERT INTO member (email, password_hash, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'UNVERIFIED', 'USER', ?, ?)",
                TestSequence.email(),
                "x".repeat(60),
                TestSequence.nickname(),
                now,
                now);
        return jdbc.queryForObject("SELECT MAX(id) FROM member", Long.class);
    }

    private int count() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record", Integer.class);
        return count == null ? 0 : count;
    }
}
