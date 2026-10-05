package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.member.domain.PasswordResetRequestPolicy.Rule;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordResetThrottleTest {

    private static final Instant START = Instant.parse("2026-10-05T03:00:00Z");
    private static final Duration DAY = Duration.ofHours(24);
    private static final Duration HOUR = Duration.ofHours(1);

    private static PasswordResetThrottle openAt(Instant now) {
        return PasswordResetThrottle.open("throttle-key", now);
    }

    /** 간격 제한이 있는 규칙에서 한도 안으로 요청을 센다. 요청마다 간격을 지켜서 센다. */
    private static void recordEvery(PasswordResetThrottle throttle, Rule rule, Duration step, int times) {
        for (int i = 0; i < times; i++) {
            throttle.record(rule, START.plus(step.multipliedBy(i)));
        }
    }

    @Test
    @DisplayName("[F-02][PW-05] open 직후에는 요청이 없고 구간이 그 시각에 시작한다")
    void openStartsWithoutRequests() {
        // when
        PasswordResetThrottle throttle = openAt(START);

        // then
        assertThat(throttle.getKey()).isEqualTo("throttle-key");
        assertThat(throttle.getWindowStart()).isEqualTo(START);
        assertThat(throttle.getRequestCount()).isZero();
        assertThat(throttle.getLastRequestAt()).isNull();
        assertThat(throttle.getCreatedAt()).isEqualTo(START);
        assertThat(throttle.getUpdatedAt()).isEqualTo(START);
        assertThat(throttle.blockedFor(PasswordResetRequestPolicy.EMAIL, START)).isEmpty();
        assertThat(throttle.blockedFor(PasswordResetRequestPolicy.IP, START)).isEmpty();
    }

    @Nested
    class EmailRule {

        private final Rule rule = PasswordResetRequestPolicy.EMAIL;

        @Test
        @DisplayName("[F-02][PW-05] 첫 요청은 통과하고 record가 횟수와 마지막 요청 시각을 남긴다")
        void firstRequestPasses() {
            // given
            PasswordResetThrottle throttle = openAt(START);

            // when
            Instant now = START.plusSeconds(5);
            assertThat(throttle.blockedFor(rule, now)).isEmpty();
            throttle.record(rule, now);

            // then
            assertThat(throttle.getRequestCount()).isEqualTo(1);
            assertThat(throttle.getLastRequestAt()).isEqualTo(now);
            assertThat(throttle.getUpdatedAt()).isEqualTo(now);
            assertThat(throttle.getWindowStart()).isEqualTo(START);
        }

        @Test
        @DisplayName("[F-02][PW-05] 이전 요청 59초 뒤는 막히고 남은 시간이 1초다")
        void blockedBeforeMinInterval() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            throttle.record(rule, START);

            // when
            var blocked = throttle.blockedFor(rule, START.plusSeconds(59));

            // then
            assertThat(blocked).contains(Duration.ofSeconds(1));
        }

        @Test
        @DisplayName("[F-02][PW-05] 이전 요청 정확히 60초 뒤는 통과한다")
        void passesExactlyAtMinInterval() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            throttle.record(rule, START);

            // then
            assertThat(throttle.blockedFor(rule, START.plusSeconds(60))).isEmpty();
        }

        @Test
        @DisplayName("[F-02][PW-05] 간격을 지키면 5번째까지 통과하고 6번째는 구간이 끝날 때까지 남은 시간만큼 막힌다")
        void sixthRequestIsBlockedUntilWindowEnds() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            recordEvery(throttle, rule, Duration.ofSeconds(60), 5);
            Instant sixthAt = START.plusSeconds(300);

            // when
            var blocked = throttle.blockedFor(rule, sixthAt);

            // then
            assertThat(throttle.getRequestCount()).isEqualTo(5);
            assertThat(blocked).contains(DAY.minusSeconds(300));
        }

        @Test
        @DisplayName("[F-02][PW-05] 구간이 끝나기 1초 전에는 막히고 남은 시간이 1초다")
        void blockedOneSecondBeforeWindowEnds() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            recordEvery(throttle, rule, Duration.ofSeconds(60), 5);

            // when
            var blocked = throttle.blockedFor(rule, START.plus(DAY).minusSeconds(1));

            // then
            assertThat(blocked).contains(Duration.ofSeconds(1));
        }

        @Test
        @DisplayName("[F-02][PW-05] 구간이 끝나는 정각에는 통과하고, 세면 새 구간이 열려 횟수가 1이 된다")
        void passesAtWindowEndAndOpensNewWindow() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            recordEvery(throttle, rule, Duration.ofSeconds(60), 5);
            Instant windowEnd = START.plus(DAY);

            // when
            assertThat(throttle.blockedFor(rule, windowEnd)).isEmpty();
            throttle.record(rule, windowEnd);

            // then
            assertThat(throttle.getRequestCount()).isEqualTo(1);
            assertThat(throttle.getWindowStart()).isEqualTo(windowEnd);
            assertThat(throttle.getLastRequestAt()).isEqualTo(windowEnd);
            assertThat(throttle.blockedFor(rule, windowEnd.plusSeconds(30))).contains(Duration.ofSeconds(30));
        }

        @Test
        @DisplayName("[F-02][PW-05] 간격만 막을 때는 간격이 남은 시간이다")
        void intervalIsReportedWhenOnlyIntervalBlocks() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            throttle.record(rule, START);

            // when
            var blocked = throttle.blockedFor(rule, START.plusSeconds(10));

            // then
            assertThat(blocked).contains(Duration.ofSeconds(50));
        }

        @Test
        @DisplayName("[F-02][PW-05] 간격과 한도가 둘 다 막는데 간격이 더 길면 간격의 남은 시간이다")
        void longerRemainingWinsWhenIntervalIsLonger() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            for (int i = 0; i < 4; i++) {
                throttle.record(rule, START.plusSeconds(60L * i));
            }
            Instant fifthAt = START.plus(DAY).minusSeconds(30);
            throttle.record(rule, fifthAt);

            // when
            var blocked = throttle.blockedFor(rule, fifthAt.plusSeconds(10));

            // then
            // 간격은 50초, 한도(구간 끝)는 20초 남았다.
            assertThat(blocked).contains(Duration.ofSeconds(50));
        }

        @Test
        @DisplayName("[F-02][PW-05] 간격과 한도가 둘 다 막는데 한도가 더 길면 한도의 남은 시간이다")
        void longerRemainingWinsWhenLimitIsLonger() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            recordEvery(throttle, rule, Duration.ofSeconds(60), 5);

            // when
            var blocked = throttle.blockedFor(rule, START.plusSeconds(250));

            // then
            // 간격은 50초, 한도는 하루에서 250초를 뺀 만큼 남았다.
            assertThat(blocked).contains(DAY.minusSeconds(250));
        }

        @Test
        @DisplayName("[F-02][PW-05] 막힌 상태에서 record를 부르면 값을 싣지 않은 IllegalStateException이고 상태는 그대로다")
        void recordWhileBlockedThrows() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            throttle.record(rule, START);
            Instant blockedAt = START.plusSeconds(30);

            // then
            assertThatThrownBy(() -> throttle.record(rule, blockedAt))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContainingAny("throttle-key", "2026", "30");
            assertThat(throttle.getRequestCount()).isEqualTo(1);
            assertThat(throttle.getLastRequestAt()).isEqualTo(START);
            assertThat(throttle.getUpdatedAt()).isEqualTo(START);
        }

        @Test
        @DisplayName("[F-02][PW-05] 한도로 막힌 상태에서도 record는 예외이고 횟수가 늘지 않는다")
        void recordWhileLimitedThrows() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            recordEvery(throttle, rule, Duration.ofSeconds(60), 5);
            Instant sixthAt = START.plusSeconds(300);

            // then
            assertThatThrownBy(() -> throttle.record(rule, sixthAt)).isInstanceOf(IllegalStateException.class);
            assertThat(throttle.getRequestCount()).isEqualTo(5);
        }
    }

    @Nested
    class IpRule {

        private final Rule rule = PasswordResetRequestPolicy.IP;

        @Test
        @DisplayName("[F-02][PW-05] 간격 없이 같은 시각에도 20번까지 통과한다")
        void twentyRequestsPassWithoutInterval() {
            // given
            PasswordResetThrottle throttle = openAt(START);

            // when
            for (int i = 0; i < 20; i++) {
                assertThat(throttle.blockedFor(rule, START)).isEmpty();
                throttle.record(rule, START);
            }

            // then
            assertThat(throttle.getRequestCount()).isEqualTo(20);
        }

        @Test
        @DisplayName("[F-02][PW-05] 21번째는 구간이 끝날 때까지 남은 시간만큼 막힌다")
        void twentyFirstRequestIsBlocked() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            for (int i = 0; i < 20; i++) {
                throttle.record(rule, START.plusSeconds(i));
            }

            // when
            var blocked = throttle.blockedFor(rule, START.plusSeconds(20));

            // then
            assertThat(blocked).contains(HOUR.minusSeconds(20));
        }

        @Test
        @DisplayName("[F-02][PW-05] 구간 시작부터 정확히 1시간 뒤에는 풀리고 새 구간이 열린다")
        void unblocksExactlyAtOneHour() {
            // given
            PasswordResetThrottle throttle = openAt(START);
            for (int i = 0; i < 20; i++) {
                throttle.record(rule, START);
            }
            assertThat(throttle.blockedFor(rule, START.plus(HOUR).minusSeconds(1)))
                    .contains(Duration.ofSeconds(1));

            // when
            Instant windowEnd = START.plus(HOUR);
            assertThat(throttle.blockedFor(rule, windowEnd)).isEmpty();
            throttle.record(rule, windowEnd);

            // then
            assertThat(throttle.getRequestCount()).isEqualTo(1);
            assertThat(throttle.getWindowStart()).isEqualTo(windowEnd);
        }
    }
}
