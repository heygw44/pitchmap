package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SanctionTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");

    @Test
    @DisplayName("[SN-05] 임시 정지는 시작 시각부터 72시간이고, 단계와 만든 관리자 없이 적용 중 상태로 만들어진다")
    void temporarySuspensionLastsSeventyTwoHours() {
        Sanction sanction = Sanction.temporary(11L, 5L, NOW);

        assertThat(sanction.getMemberId()).isEqualTo(11L);
        assertThat(sanction.getReportId()).isEqualTo(5L);
        assertThat(sanction.getType()).isEqualTo(SanctionType.TEMPORARY_72H);
        assertThat(sanction.getLevel()).isNull();
        assertThat(sanction.getStartsAt()).isEqualTo(NOW);
        assertThat(sanction.getEndsAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
        assertThat(sanction.getStatus()).isEqualTo(SanctionStatus.ACTIVE);
        assertThat(sanction.getCreatedBy()).isNull();
        assertThat(sanction.getReason()).isNotBlank();
    }

    @Test
    @DisplayName("시작 시각이 null이면 임시 정지를 만들지 못한다")
    void rejectsNullStart() {
        assertThatThrownBy(() -> Sanction.temporary(11L, 5L, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
