package com.pitchmap.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("[NFR-03] 선점한 직후의 기록은 응답이 없어 완료되지 않았다")
    void claimedRecordIsNotCompleted() {
        IdempotencyRecord record = IdempotencyRecord.claim(1L, "key-1", HASH, NOW);

        assertThat(record.isCompleted()).isFalse();
        assertThat(record.getResponseStatus()).isNull();
        assertThat(record.getResponseBody()).isNull();
        assertThat(record.isNew()).isTrue();
        assertThat(record.getId()).isEqualTo(new IdempotencyRecordId(1L, "key-1"));
    }

    @Test
    @DisplayName("[NFR-03] 결과를 채우면 완료된 기록이 된다")
    void completeFillsResponse() {
        IdempotencyRecord record = IdempotencyRecord.claim(1L, "key-1", HASH, NOW);

        record.complete(201, "{\"id\":7}");

        assertThat(record.isCompleted()).isTrue();
        assertThat(record.getResponseStatus()).isEqualTo((short) 201);
        assertThat(record.getResponseBody()).isEqualTo("{\"id\":7}");
    }

    @Test
    @DisplayName("[NFR-03] 요청 해시가 같을 때만 matches가 true다")
    void matchesComparesRequestHash() {
        IdempotencyRecord record = IdempotencyRecord.claim(1L, "key-1", HASH, NOW);

        assertThat(record.matches(HASH)).isTrue();
        assertThat(record.matches("b".repeat(64))).isFalse();
    }

    @Test
    @DisplayName("[NFR-03] 키, 해시, 시각이 비어 있으면 선점을 거부한다")
    void claimRejectsBlankArguments() {
        assertThatThrownBy(() -> IdempotencyRecord.claim(1L, " ", HASH, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyRecord.claim(1L, "key-1", "", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyRecord.claim(1L, "key-1", HASH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
