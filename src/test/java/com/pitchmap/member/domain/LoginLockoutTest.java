package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginLockoutTest {

    private final MutableClock clock = MutableClock.atDefaultInstant();

    @Test
    @DisplayName("[PW-03] 실패가 4건이면 잠그지 않는다")
    void fourFailuresDoNotLock() {
        List<Instant> failures = failuresMinutesAgo(4, 3, 2, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, now(clock));

        assertThat(lockedUntil).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 10분 안에 실패가 5건이면 가장 오래된 실패로부터 10분 뒤까지 잠근다")
    void fiveFailuresLockUntilTenMinutesAfterOldestFailure() {
        List<Instant> failures = failuresMinutesAgo(9, 7, 5, 3, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, now(clock));

        assertThat(lockedUntil).contains(now(clock).minus(Duration.ofMinutes(9)).plus(LoginLockout.WINDOW));
    }

    @Test
    @DisplayName("[PW-03] 가장 오래된 실패가 정확히 10분 전이면 구간 밖이라 실패를 4건으로 세고 잠그지 않는다")
    void failureExactlyTenMinutesAgoIsOutsideWindow() {
        List<Instant> failures = failuresMinutesAgo(10, 6, 4, 2, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, now(clock));

        assertThat(lockedUntil).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 잠금이 풀리는 시각이 되면 잠금이 풀린다")
    void lockIsReleasedAtUnlockInstant() {
        List<Instant> failures = failuresMinutesAgo(0, 0, 0, 0, 0);
        Instant lockedUntil = LoginLockout.lockedUntil(failures, now(clock)).orElseThrow();

        clock.setInstant(lockedUntil.minusSeconds(1));
        assertThat(LoginLockout.lockedUntil(failures, now(clock))).isPresent();

        clock.setInstant(lockedUntil);
        assertThat(LoginLockout.lockedUntil(failures, now(clock))).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 실패가 5건을 넘으면 구간 안 가장 최근 5건 중 가장 오래된 실패를 기준으로 잠금이 풀린다")
    void moreThanFiveFailuresUseOldestOfLatestFive() {
        List<Instant> failures = failuresMinutesAgo(9, 8, 6, 4, 3, 2, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, now(clock));

        assertThat(lockedUntil).contains(now(clock).minus(Duration.ofMinutes(6)).plus(LoginLockout.WINDOW));
    }

    @Test
    @DisplayName("[PW-03] 10분이 지난 실패는 세지 않는다")
    void failuresOlderThanWindowAreIgnored() {
        List<Instant> failures = failuresMinutesAgo(30, 20, 15, 3, 1);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, now(clock));

        assertThat(lockedUntil).isEmpty();
    }

    @Test
    @DisplayName("[PW-03] 실패 시각을 어떤 순서로 넘겨도 같은 결과를 낸다")
    void failureOrderDoesNotMatter() {
        List<Instant> failures = failuresMinutesAgo(1, 9, 3, 7, 5);

        Optional<Instant> lockedUntil = LoginLockout.lockedUntil(failures, now(clock));

        assertThat(lockedUntil).contains(now(clock).minus(Duration.ofMinutes(9)).plus(LoginLockout.WINDOW));
    }

    @Test
    @DisplayName("[PW-03] 실패가 없으면 잠그지 않는다")
    void noFailuresDoNotLock() {
        assertThat(LoginLockout.lockedUntil(List.of(), now(clock))).isEmpty();
    }

    private static Instant now(Clock clock) {
        return Instant.now(clock);
    }

    private List<Instant> failuresMinutesAgo(long... minutesAgo) {
        List<Instant> failures = new ArrayList<>();
        for (long minutes : minutesAgo) {
            failures.add(now(clock).minus(Duration.ofMinutes(minutes)));
        }
        return failures;
    }
}
