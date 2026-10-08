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

    @Test
    @DisplayName("[SN-10] 경고는 끝나는 시각 없이 1단계로 만들어지고 신고와 관리자를 기록한다")
    void warningHasNoEnd() {
        Sanction sanction = Sanction.confirm(11L, 5L, SanctionType.WARNING, 1, "욕설", NOW, 99L);

        assertThat(sanction.getType()).isEqualTo(SanctionType.WARNING);
        assertThat(sanction.getLevel()).isEqualTo((byte) 1);
        assertThat(sanction.getReportId()).isEqualTo(5L);
        assertThat(sanction.getReason()).isEqualTo("욕설");
        assertThat(sanction.getStartsAt()).isEqualTo(NOW);
        assertThat(sanction.getEndsAt()).isNull();
        assertThat(sanction.getStatus()).isEqualTo(SanctionStatus.ACTIVE);
        assertThat(sanction.getCreatedBy()).isEqualTo(99L);
    }

    @Test
    @DisplayName("[SN-10] 7일 정지는 시작 시각에서 7일, 30일 정지는 30일 뒤에 끝난다")
    void suspensionEndsAfterItsPeriod() {
        Sanction seven = Sanction.confirm(11L, null, SanctionType.SUSPEND_7D, 2, "반복 위반", NOW, 99L);
        Sanction thirty = Sanction.confirm(11L, null, SanctionType.SUSPEND_30D, 3, "반복 위반", NOW, 99L);

        assertThat(seven.getEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(seven.getReportId()).isNull();
        assertThat(thirty.getEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("[SN-10] 영구 정지는 4단계이고 끝나는 시각이 없다")
    void permanentHasNoEnd() {
        Sanction sanction = Sanction.confirm(11L, null, SanctionType.PERMANENT, 4, "심각한 위반", NOW, 99L);

        assertThat(sanction.getLevel()).isEqualTo((byte) 4);
        assertThat(sanction.getEndsAt()).isNull();
    }

    @Test
    @DisplayName("확정 제재는 임시 정지 종류, 종류와 맞지 않는 단계, 빈 사유나 500자를 넘는 사유, null 시작 시각을 거부한다")
    void confirmRejectsInvalidValues() {
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.TEMPORARY_72H, 1, "사유", NOW, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.WARNING, 2, "사유", NOW, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.WARNING, 1, " ", NOW, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.WARNING, 1, "가".repeat(501), NOW, 99L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sanction.confirm(11L, null, SanctionType.WARNING, 1, "사유", null, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
