package com.pitchmap.common.idempotency;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 같은 회원이 같은 멱등성 키로 보낸 요청을 한 번만 처리하고, 이후 요청에는 처음 결과를 돌려준다.
 *
 * <p>호출하는 쪽의 컨트롤러는 트랜잭션 밖에서 {@link #execute}를 부르고, 업무 처리는 {@code action}으로 넘긴다.
 * {@code action} 안의 {@code application} 서비스가 자기 {@code @Transactional}로 커밋한다. 이 클래스는 {@code common}에 있어
 * 트랜잭션을 직접 열지 않는다. 대신 아래 단계를 Spring Data 기본 메서드로 하나씩 짧게 커밋한다.
 *
 * <ol>
 *   <li>선점: 응답 없는 행을 INSERT해 커밋한다. 다른 요청이 곧바로 이 행을 보고 처리 중 오류를 받는다.
 *   <li>업무 처리: {@code action}을 실행한다.
 *   <li>완료 기록: 성공 결과의 상태 코드와 본문을 행에 채운다.
 *   <li>실패: {@code action}이 예외로 끝나면 선점 행을 지운다. 같은 키로 다시 보내면 처음부터 다시 처리한다.
 * </ol>
 *
 * <p>저장하는 것은 성공 결과뿐이다. 서버가 업무 처리 도중 멈추면 선점 행이 응답 없이 남아, 정리 작업이 지울 때까지 같은 키의 요청은
 * 처리 중 오류를 받는다. 결과가 두 건이 되는 일은 없다.
 *
 * <p>키, 요청 본문, 응답 본문은 로그에 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyExecutor {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final IdempotencyRecordJpaRepository recordRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * 호출하면 멱등성 키를 검사하고 선점한 뒤 {@code action}을 실행해 결과를 저장한다. 이미 완료된 키면 {@code action}을 부르지 않고 저장된
     * 결과를 돌려준다.
     *
     * @throws BusinessException 키가 없으면 {@code IDEMPOTENCY_KEY_REQUIRED}, 형식이 틀리면 {@code INVALID_INPUT},
     *     같은 키로 다른 요청이면 {@code IDEMPOTENCY_KEY_REUSED}, 처리 중이면 {@code IDEMPOTENCY_IN_PROGRESS}
     * @throws IllegalStateException 트랜잭션 안에서 부른 경우
     */
    public <T> T execute(
            IdempotentRequest request, HttpStatus successStatus, Class<T> responseType, Supplier<T> action) {
        requireNoTransaction();
        String key = validateKey(request.key());
        String requestHash = hash(request);

        IdempotencyRecord record = IdempotencyRecord.claim(request.memberId(), key, requestHash, Instant.now(clock));
        if (!tryClaim(record)) {
            Optional<T> replayed = replayExisting(request.memberId(), key, requestHash, responseType);
            if (replayed.isPresent()) {
                return replayed.get();
            }
            // 선점했던 요청이 그 사이 실패해서 행을 지웠다. 한 번만 다시 선점해 본다.
            record = IdempotencyRecord.claim(request.memberId(), key, requestHash, Instant.now(clock));
            if (!tryClaim(record)) {
                throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
            }
        }

        T result = runAction(record, action);
        recordCompletion(record, successStatus, result, request.memberId());
        return result;
    }

    // 바깥 트랜잭션에 합류하면 선점 행이 바깥 트랜잭션이 끝날 때에야 커밋된다. 그러면 처리 중인 동안 들어온 요청이
    // 선점 행을 보지 못해 같은 요청을 한 번 더 처리한다.
    private void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("멱등성 처리는 트랜잭션 밖에서 호출해야 합니다.");
        }
    }

    private String validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "멱등성 키는 영문, 숫자, '-', '_'로 이루어진 1~64자여야 합니다.");
        }
        return key;
    }

    // 같은 operation과 같은 본문이면 같은 해시가 나와야 한다. 구분자를 줄바꿈으로 둬서 operation 끝과 본문 시작이 섞이지 않게 한다.
    private String hash(IdempotentRequest request) {
        String source = request.operation() + "\n" + payloadJson(request.payload());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    // 본문에는 개인정보가 들어갈 수 있으므로 예외 메시지에 값을 싣지 않는다.
    private String payloadJson(Object payload) {
        if (payload == null) {
            return "";
        }
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "요청 본문을 JSON으로 바꿀 수 없습니다. 본문 타입=" + payload.getClass().getName(), e);
        }
    }

    private boolean tryClaim(IdempotencyRecord record) {
        try {
            recordRepository.saveAndFlush(record);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    // 선점에 실패했을 때 기존 행을 보고 판단한다. 행이 사라졌으면 빈 값을 돌려준다.
    private <T> Optional<T> replayExisting(long memberId, String key, String requestHash, Class<T> responseType) {
        Optional<IdempotencyRecord> existing = recordRepository.findById(new IdempotencyRecordId(memberId, key));
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyRecord found = existing.get();
        if (!found.matches(requestHash)) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (!found.isCompleted()) {
            throw new BusinessException(CommonErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        try {
            return Optional.of(jsonMapper.readValue(found.getResponseBody(), responseType));
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 멱등성 응답을 읽을 수 없습니다. 응답 타입=" + responseType.getName(), e);
        }
    }

    private <T> T runAction(IdempotencyRecord record, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            releaseClaim(record, e);
            throw e;
        }
    }

    // 지우기가 실패해도 원래 예외를 가리지 않도록 suppressed로 붙인다. 이 경우 행은 정리 작업이 지울 때까지 남는다.
    private void releaseClaim(IdempotencyRecord record, RuntimeException original) {
        try {
            recordRepository.deleteById(record.getId());
        } catch (RuntimeException deleteFailure) {
            original.addSuppressed(deleteFailure);
        }
    }

    // 업무 처리는 이미 커밋됐다. 기록에 실패해도 호출한 쪽에는 성공 결과를 돌려주고, 같은 키의 다음 요청은 처리 중 오류를 받는다.
    private <T> void recordCompletion(IdempotencyRecord record, HttpStatus successStatus, T result, long memberId) {
        try {
            String body = jsonMapper.writeValueAsString(result);
            recordRepository.save(record.complete(successStatus.value(), body));
        } catch (RuntimeException e) {
            // 예외 메시지에 SQL 인자(응답 본문)가 실릴 수 있어 예외 객체를 로그에 넘기지 않고 타입만 남긴다.
            log.error(
                    "idempotency completion record failed memberId={} errorType={}",
                    memberId,
                    e.getClass().getName());
        }
    }
}
