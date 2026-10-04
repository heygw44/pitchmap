package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.EmailVerificationPolicy.ResendHistory;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailVerificationPolicyTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Test
    @DisplayName("[F-01][EV-03] 발송 이력이 없으면 재발송할 수 있다")
    void noHistoryAllowsResend() {
        assertThat(EmailVerificationPolicy.resendBlockedFor(history(), NOW)).isEmpty();
    }

    @Test
    @DisplayName("[F-01][EV-03] 직전 발송 후 59초에는 1초 더 기다려야 하고 60초가 지나면 재발송할 수 있다")
    void resendIntervalBoundaryIsSixtySeconds() {
        // given
        ResendHistory at59Seconds =
                new HistoryBuilder().lastSentAt(NOW.minusSeconds(59)).build();
        ResendHistory at60Seconds =
                new HistoryBuilder().lastSentAt(NOW.minusSeconds(60)).build();
        ResendHistory justSent = new HistoryBuilder().lastSentAt(NOW).build();

        // when & then
        assertThat(EmailVerificationPolicy.resendBlockedFor(at59Seconds, NOW)).contains(Duration.ofSeconds(1));
        assertThat(EmailVerificationPolicy.resendBlockedFor(at60Seconds, NOW)).isEmpty();
        assertThat(EmailVerificationPolicy.resendBlockedFor(justSent, NOW)).contains(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("[F-01][EV-03] 24시간 안에 4건까지는 재발송할 수 있고 5건이면 막는다")
    void dailyLimitBlocksAtFiveSends() {
        // given
        Instant oldest = NOW.minus(Duration.ofHours(10));
        Instant lastSentAt = NOW.minus(Duration.ofMinutes(5));
        ResendHistory fourSends =
                new HistoryBuilder().lastSentAt(lastSentAt).daily(4, oldest).build();
        ResendHistory fiveSends =
                new HistoryBuilder().lastSentAt(lastSentAt).daily(5, oldest).build();

        // when & then
        assertThat(EmailVerificationPolicy.resendBlockedFor(fourSends, NOW)).isEmpty();
        assertThat(EmailVerificationPolicy.resendBlockedFor(fiveSends, NOW)).contains(Duration.ofHours(14));
    }

    @Test
    @DisplayName("[F-01][EV-03] 하루 한도에 걸린 뒤 가장 오래된 발송이 24시간 지나는 순간 풀린다")
    void dailyLimitIsReleasedWhenOldestSendIsTwentyFourHoursOld() {
        // given
        Instant lastSentAt = NOW.minus(Duration.ofHours(1));
        ResendHistory justBefore = new HistoryBuilder()
                .lastSentAt(lastSentAt)
                .daily(5, NOW.minus(Duration.ofHours(24)).plusSeconds(1))
                .build();
        ResendHistory exactly = new HistoryBuilder()
                .lastSentAt(lastSentAt)
                .daily(5, NOW.minus(Duration.ofHours(24)))
                .build();

        // when & then
        assertThat(EmailVerificationPolicy.resendBlockedFor(justBefore, NOW)).contains(Duration.ofSeconds(1));
        assertThat(EmailVerificationPolicy.resendBlockedFor(exactly, NOW)).isEmpty();
    }

    @Test
    @DisplayName("[F-01][EV-03] 같은 IP에서 1시간 안에 19건까지는 재발송할 수 있고 20건이면 막는다")
    void ipLimitBlocksAtTwentySends() {
        // given
        Instant oldest = NOW.minus(Duration.ofMinutes(40));
        ResendHistory nineteenSends = new HistoryBuilder().ip(19, oldest).build();
        ResendHistory twentySends = new HistoryBuilder().ip(20, oldest).build();

        // when & then
        assertThat(EmailVerificationPolicy.resendBlockedFor(nineteenSends, NOW)).isEmpty();
        assertThat(EmailVerificationPolicy.resendBlockedFor(twentySends, NOW)).contains(Duration.ofMinutes(20));
    }

    @Test
    @DisplayName("[F-01][EV-03] IP 한도에 걸린 뒤 가장 오래된 발송이 1시간 지나는 순간 풀린다")
    void ipLimitIsReleasedWhenOldestSendIsOneHourOld() {
        // given
        ResendHistory justBefore = new HistoryBuilder()
                .ip(20, NOW.minus(Duration.ofHours(1)).plusSeconds(1))
                .build();
        ResendHistory exactly =
                new HistoryBuilder().ip(20, NOW.minus(Duration.ofHours(1))).build();

        // when & then
        assertThat(EmailVerificationPolicy.resendBlockedFor(justBefore, NOW)).contains(Duration.ofSeconds(1));
        assertThat(EmailVerificationPolicy.resendBlockedFor(exactly, NOW)).isEmpty();
    }

    @Test
    @DisplayName("[F-01][EV-03] 처리하지 않은 발송 요청이 있으면 직전 발송이 오래전이어도 60초 동안 막는다")
    void pendingSendRequestBlocksForResendInterval() {
        // given
        ResendHistory noPreviousSend = new HistoryBuilder().sendPending(true).build();
        ResendHistory oldPreviousSend = new HistoryBuilder()
                .lastSentAt(NOW.minus(Duration.ofHours(3)))
                .sendPending(true)
                .build();

        // when & then
        assertThat(EmailVerificationPolicy.resendBlockedFor(noPreviousSend, NOW))
                .contains(Duration.ofSeconds(60));
        assertThat(EmailVerificationPolicy.resendBlockedFor(oldPreviousSend, NOW))
                .contains(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("[F-01][EV-03] 막은 이유가 여럿이면 풀리기까지 가장 긴 시간을 돌려준다")
    void returnsLongestRemainingTimeWhenSeveralReasonsApply() {
        // given
        ResendHistory history = new HistoryBuilder()
                .lastSentAt(NOW.minusSeconds(20))
                .daily(5, NOW.minus(Duration.ofHours(19)))
                .ip(20, NOW.minus(Duration.ofMinutes(50)))
                .sendPending(true)
                .build();

        // when
        Optional<Duration> blockedFor = EmailVerificationPolicy.resendBlockedFor(history, NOW);

        // then
        assertThat(blockedFor).contains(Duration.ofHours(5));
    }

    @Test
    @DisplayName("[F-01][EV-03] 직전 발송 간격이 가장 길면 그 시간을 돌려준다")
    void returnsIntervalRemainingWhenItIsTheLongest() {
        // given
        ResendHistory history = new HistoryBuilder()
                .lastSentAt(NOW.minusSeconds(10))
                .daily(4, NOW.minus(Duration.ofHours(19)))
                .ip(19, NOW.minus(Duration.ofMinutes(50)))
                .build();

        // when & then
        assertThat(EmailVerificationPolicy.resendBlockedFor(history, NOW)).contains(Duration.ofSeconds(50));
    }

    @Test
    @DisplayName("[F-01][EV-03] 발송 횟수와 가장 오래된 발송 시각이 서로 맞지 않는 이력은 만들 수 없다")
    void inconsistentHistoryIsRejected() {
        assertThatThrownBy(() -> new ResendHistory(null, 0, NOW, 0, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResendHistory(null, 3, null, 0, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResendHistory(null, 0, null, 2, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResendHistory(null, 0, null, 0, NOW, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResendHistory(null, -1, NOW, 0, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ResendHistory history() {
        return new HistoryBuilder().build();
    }

    private static final class HistoryBuilder {

        private Instant lastSentAt;
        private int sentInDay;
        private Instant oldestSentInDay;
        private int sentByIpInHour;
        private Instant oldestSentByIpInHour;
        private boolean sendPending;

        HistoryBuilder lastSentAt(Instant lastSentAt) {
            this.lastSentAt = lastSentAt;
            return this;
        }

        HistoryBuilder daily(int count, Instant oldest) {
            this.sentInDay = count;
            this.oldestSentInDay = oldest;
            return this;
        }

        HistoryBuilder ip(int count, Instant oldest) {
            this.sentByIpInHour = count;
            this.oldestSentByIpInHour = oldest;
            return this;
        }

        HistoryBuilder sendPending(boolean sendPending) {
            this.sendPending = sendPending;
            return this;
        }

        ResendHistory build() {
            return new ResendHistory(
                    lastSentAt, sentInDay, oldestSentInDay, sentByIpInHour, oldestSentByIpInHour, sendPending);
        }
    }
}
