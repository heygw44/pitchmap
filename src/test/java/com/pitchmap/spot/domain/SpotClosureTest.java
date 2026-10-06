package com.pitchmap.spot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.spot.application.PublicSpotOperatingStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpotClosureTest {

    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate UNTIL = LocalDate.of(2026, 11, 30);

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"2026-11-15, true", "2026-11-01, true", "2026-11-30, true", "2026-10-31, false", "2026-12-01, false"})
    @DisplayName("[F-03] 휴장 기간이 있으면 시작일과 종료일을 포함한 기간 안에서만 휴장이다")
    void closedOnlyInsidePeriodInclusive(LocalDate today, boolean expected) {
        // given
        SpotClosure closure = new SpotClosure(null, FROM, UNTIL);

        // when
        boolean closed = closure.isClosedOn(today);

        // then
        assertThat(closed).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"2026-11-01, true", "2027-05-01, true", "2026-10-31, false"})
    @DisplayName("[F-03] 시작일만 있으면 시작일부터 계속 휴장이다")
    void onlyClosedFromIsOpenEnded(LocalDate today, boolean expected) {
        // given
        SpotClosure closure = new SpotClosure(null, FROM, null);

        // when
        boolean closed = closure.isClosedOn(today);

        // then
        assertThat(closed).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"2025-01-01, true", "2026-11-30, true", "2026-12-01, false"})
    @DisplayName("[F-03] 종료일만 있으면 종료일까지 휴장이다")
    void onlyClosedUntilIsOpenStarted(LocalDate today, boolean expected) {
        // given
        SpotClosure closure = new SpotClosure(null, null, UNTIL);

        // when
        boolean closed = closure.isClosedOn(today);

        // then
        assertThat(closed).isEqualTo(expected);
    }

    @Test
    @DisplayName("[F-03] 기간이 끝났으면 운영 상태가 임시 휴장이어도 휴장이 아니다")
    void endedPeriodOverridesTemporarilyClosed() {
        // given
        SpotClosure closure = new SpotClosure(PublicSpotOperatingStatus.TEMPORARILY_CLOSED, FROM, UNTIL);

        // when
        boolean closed = closure.isClosedOn(LocalDate.of(2026, 12, 15));

        // then
        assertThat(closed).isFalse();
    }

    @Test
    @DisplayName("[F-03] 기간 안이면 운영 상태가 운영 중이어도 휴장이다")
    void currentPeriodOverridesOperating() {
        // given
        SpotClosure closure = new SpotClosure(PublicSpotOperatingStatus.OPERATING, FROM, UNTIL);

        // when
        boolean closed = closure.isClosedOn(LocalDate.of(2026, 11, 15));

        // then
        assertThat(closed).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"TEMPORARILY_CLOSED, true", "PERMANENTLY_CLOSED, true", "OPERATING, false", ", false"})
    @DisplayName("[F-03] 기간이 없으면 운영 상태가 임시 휴장이나 폐업일 때만 휴장이다")
    void withoutPeriodUsesOperatingStatus(PublicSpotOperatingStatus status, boolean expected) {
        // given
        SpotClosure closure = new SpotClosure(status, null, null);

        // when
        boolean closed = closure.isClosedOn(LocalDate.of(2026, 11, 15));

        // then
        assertThat(closed).isEqualTo(expected);
    }

    @Test
    @DisplayName("판단할 날짜가 null이면 NullPointerException이다")
    void rejectsNullToday() {
        // given
        SpotClosure closure = new SpotClosure(null, null, null);

        // when, then
        assertThatThrownBy(() -> closure.isClosedOn(null)).isInstanceOf(NullPointerException.class);
    }
}
