package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampBuilder.DEFAULT_START_DATE;
import static com.pitchmap.basecamp.domain.BasecampBuilder.MEMBER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.aBasecamp;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EarlyLeaveTest {

    private static final Instant DEPARTURE =
            DEFAULT_START_DATE.atStartOfDay(Basecamp.KOREA).toInstant();
    private static final Instant WINDOW_START = DEPARTURE.minus(Duration.ofHours(48));

    private static boolean leaveConfirmedAt(Instant leaveTime) {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.CONFIRMED);
        basecamp.leave(MEMBER_ID, leaveTime);
        return basecamp.getMembers().stream()
                .filter(member -> member.getMemberId() == MEMBER_ID)
                .findFirst()
                .orElseThrow()
                .isEarlyLeave();
    }

    @Test
    @DisplayName("[BC-21] 출발 시각은 출발일 0시(한국 시간)다")
    void departureIsMidnightKorea() {
        assertThat(DEPARTURE).isEqualTo(Instant.parse("2026-10-19T15:00:00Z"));
    }

    @Test
    @DisplayName("[BC-21] 출발 48시간 전 정각에 탈퇴하면 임박 탈퇴다")
    void leaveExactlyAtWindowStartIsEarly() {
        assertThat(leaveConfirmedAt(WINDOW_START)).isTrue();
    }

    @Test
    @DisplayName("[BC-21] 출발 48시간 전보다 1나노초 먼저 탈퇴하면 임박 탈퇴가 아니다")
    void leaveOneNanoBeforeWindowIsNotEarly() {
        assertThat(leaveConfirmedAt(WINDOW_START.minusNanos(1))).isFalse();
    }

    @Test
    @DisplayName("[BC-21] 출발 48시간 전보다 1초 먼저 탈퇴하면 임박 탈퇴가 아니다")
    void leaveOneSecondBeforeWindowIsNotEarly() {
        assertThat(leaveConfirmedAt(WINDOW_START.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("[BC-21] 출발 48시간 전보다 1나노초 늦게 탈퇴하면 임박 탈퇴다")
    void leaveOneNanoAfterWindowStartIsEarly() {
        assertThat(leaveConfirmedAt(WINDOW_START.plusNanos(1))).isTrue();
    }

    @Test
    @DisplayName("[BC-21] 출발 시각 정각에 탈퇴해도 임박 탈퇴다")
    void leaveAtDepartureIsEarly() {
        assertThat(leaveConfirmedAt(DEPARTURE)).isTrue();
    }

    @Test
    @DisplayName("[BC-21] 출발 시각이 지난 뒤에 탈퇴해도 임박 탈퇴다")
    void leaveAfterDepartureIsEarly() {
        assertThat(leaveConfirmedAt(DEPARTURE.plusSeconds(3600))).isTrue();
    }

    @Test
    @DisplayName("[BC-21] 확정되지 않은 모집 중 베이스캠프에서는 출발이 임박해도 임박 탈퇴가 아니다")
    void leaveWhileRecruitingIsNeverEarly() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        basecamp.leave(MEMBER_ID, WINDOW_START);

        assertThat(basecamp.getMembers().get(1).isEarlyLeave()).isFalse();
    }

    @Test
    @DisplayName("[BC-21] 확정되지 않은 마감 베이스캠프에서는 출발이 임박해도 임박 탈퇴가 아니다")
    void leaveWhileClosedIsNeverEarly() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.CLOSED);

        basecamp.leave(MEMBER_ID, DEPARTURE);

        assertThat(basecamp.getMembers().get(1).isEarlyLeave()).isFalse();
    }

    @Test
    @DisplayName("[BC-21] 임박 탈퇴를 하면 탈퇴 시각과 상태가 함께 기록된다")
    void earlyLeaveRecordsTimeAndStatus() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.CONFIRMED);

        basecamp.leave(MEMBER_ID, DEPARTURE);

        BasecampMember member = basecamp.getMembers().get(1);
        assertThat(member.getStatus()).isEqualTo(BasecampMemberStatus.LEFT);
        assertThat(member.getLeftAt()).isEqualTo(DEPARTURE);
    }
}
