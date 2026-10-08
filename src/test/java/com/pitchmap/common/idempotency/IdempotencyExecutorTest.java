package com.pitchmap.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.MutableClock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class IdempotencyExecutorTest {

    private final IdempotencyRecordJpaRepository repository = mock(IdempotencyRecordJpaRepository.class);
    private final IdempotencyExecutor executor =
            new IdempotencyExecutor(repository, JsonMapper.builder().build(), MutableClock.atDefaultInstant());

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("[NFR-03] 키가 비어 있으면 IDEMPOTENCY_KEY_REQUIRED")
    void blankKeyIsRequired(String key) {
        assertKeyRejected(key, CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    @Test
    @DisplayName("[NFR-03] 키가 null이면 IDEMPOTENCY_KEY_REQUIRED")
    void nullKeyIsRequired() {
        assertKeyRejected(null, CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a b", "한글키", "key.with.dot", "key/slash", "key\n"})
    @DisplayName("[NFR-03] 허용하지 않는 문자가 있으면 INVALID_INPUT")
    void invalidCharactersAreRejected(String key) {
        assertKeyRejected(key, CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("[NFR-03] 키가 65자면 INVALID_INPUT")
    void tooLongKeyIsRejected() {
        assertKeyRejected("a".repeat(65), CommonErrorCode.INVALID_INPUT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "A-z_0", "12345678-1234-1234-1234-123456789012"})
    @DisplayName("[NFR-03] 허용하는 키는 통과한다")
    void validKeysPass(String key) {
        assertThat(execute(key, "POST /a", Map.of("x", 1))).isEqualTo("ok");
    }

    @Test
    @DisplayName("[NFR-03] 64자 키는 통과한다")
    void maxLengthKeyPasses() {
        assertThat(execute("a".repeat(64), "POST /a", null)).isEqualTo("ok");
    }

    @Test
    @DisplayName("[NFR-03] 같은 입력이면 같은 요청 해시, operation이나 본문이 다르면 다른 해시")
    void requestHashDependsOnOperationAndPayload() {
        String base = hashOf("POST /a", Map.of("x", 1));

        assertThat(base).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hashOf("POST /a", Map.of("x", 1))).isEqualTo(base);
        assertThat(hashOf("POST /b", Map.of("x", 1))).isNotEqualTo(base);
        assertThat(hashOf("POST /a", Map.of("x", 2))).isNotEqualTo(base);
        assertThat(hashOf("POST /a", null)).isNotEqualTo(base);
    }

    @Test
    @DisplayName("[NFR-03] 키가 잘못되면 선점을 시도하지 않는다")
    void invalidKeyDoesNotTouchRepository() {
        assertThatThrownBy(() -> execute("bad key", "POST /a", null)).isInstanceOf(BusinessException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    private void assertKeyRejected(String key, CommonErrorCode expected) {
        assertThatThrownBy(() -> execute(key, "POST /a", null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private String execute(String key, String operation, Object payload) {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return executor.execute(
                new IdempotentRequest(1L, key, operation, payload), HttpStatus.CREATED, String.class, () -> "ok");
    }

    private String hashOf(String operation, Object payload) {
        execute("key-1", operation, payload);
        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(captor.capture());
        List<IdempotencyRecord> all = captor.getAllValues();
        return all.get(all.size() - 1).getRequestHash();
    }
}
