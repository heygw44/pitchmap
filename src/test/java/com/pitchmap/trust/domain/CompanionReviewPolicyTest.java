package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanionReviewPolicyTest {

    private static final Instant COMPLETED_AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final Instant DEADLINE = COMPLETED_AT.plus(Duration.ofDays(14));

    @Test
    @DisplayName("[RV-02] 작성 기한은 완료 시각에서 14일 뒤다")
    void deadlineIsFourteenDaysAfterCompletion() {
        assertThat(CompanionReviewPolicy.deadlineOf(COMPLETED_AT)).isEqualTo(DEADLINE);
    }

    @Test
    @DisplayName("[RV-02] 기한 정각까지는 쓸 수 있고, 1초라도 지나면 쓸 수 없다")
    void writableUntilDeadlineInclusive() {
        assertThat(CompanionReviewPolicy.isWritable(COMPLETED_AT, COMPLETED_AT)).isTrue();
        assertThat(CompanionReviewPolicy.isWritable(COMPLETED_AT, DEADLINE.minusSeconds(1)))
                .isTrue();
        assertThat(CompanionReviewPolicy.isWritable(COMPLETED_AT, DEADLINE)).isTrue();
        assertThat(CompanionReviewPolicy.isWritable(COMPLETED_AT, DEADLINE.plusSeconds(1)))
                .isFalse();
    }

    @Test
    @DisplayName("[RV-03] 반대 방향 후기가 없고 기한 안이면 공개하지 않는다")
    void notRevealedWithoutReverseBeforeDeadline() {
        assertThat(CompanionReviewPolicy.isRevealed(false, COMPLETED_AT, DEADLINE))
                .isFalse();
    }

    @Test
    @DisplayName("[RV-03] 반대 방향 후기가 있으면 기한 안에도 공개한다")
    void revealedWithReverseBeforeDeadline() {
        assertThat(CompanionReviewPolicy.isRevealed(true, COMPLETED_AT, DEADLINE))
                .isTrue();
    }

    @Test
    @DisplayName("[RV-03] 반대 방향 후기가 없어도 기한이 지나면 공개한다")
    void revealedAfterDeadlineWithoutReverse() {
        assertThat(CompanionReviewPolicy.isRevealed(false, COMPLETED_AT, DEADLINE.plusSeconds(1)))
                .isTrue();
    }

    @Test
    @DisplayName("[RV-03] 반대 방향 후기가 있고 기한도 지났으면 공개한다")
    void revealedWithReverseAfterDeadline() {
        assertThat(CompanionReviewPolicy.isRevealed(true, COMPLETED_AT, DEADLINE.plusSeconds(1)))
                .isTrue();
    }

    @Test
    @DisplayName("공개 기준 시각은 지금에서 14일 전이고, 이 시각보다 먼저 완료된 베이스캠프만 기한이 지난 것이다")
    void revealCutoffMatchesDeadlineBoundary() {
        Instant now = DEADLINE;

        Instant cutoff = CompanionReviewPolicy.revealCutoff(now);

        assertThat(cutoff).isEqualTo(COMPLETED_AT);
        assertThat(COMPLETED_AT.isBefore(cutoff)).isFalse();
        assertThat(CompanionReviewPolicy.isWritable(COMPLETED_AT, now)).isTrue();
    }
}
