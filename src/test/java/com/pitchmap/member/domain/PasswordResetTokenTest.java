package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordResetTokenTest {

    private final MutableClock clock = MutableClock.atDefaultInstant();

    private PasswordResetToken issue() {
        String hash = ResetToken.hash(ResetToken.generate(new SecureRandom()).value());
        return PasswordResetToken.issue(1L, hash, clock.instant());
    }

    @Test
    @DisplayName("[F-02][PW-02] 만료 시각은 발급 시각 30분 뒤다")
    void expiresThirtyMinutesAfterIssue() {
        // when
        PasswordResetToken token = issue();

        // then
        assertThat(token.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(30)));
        assertThat(token.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("[F-02][PW-02] 만료 1초 전까지는 쓸 수 있다")
    void usableOneSecondBeforeExpiry() {
        // given
        PasswordResetToken token = issue();
        Instant now = token.getExpiresAt().minusSeconds(1);

        // then
        assertThat(token.isUsable(now)).isTrue();
    }

    @Test
    @DisplayName("[F-02][PW-02] 만료 시각 정각부터는 쓸 수 없다")
    void notUsableAtExpiry() {
        // given
        PasswordResetToken token = issue();

        // then
        assertThat(token.isUsable(token.getExpiresAt())).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-02] 만료 뒤에는 쓸 수 없다")
    void notUsableAfterExpiry() {
        // given
        PasswordResetToken token = issue();

        // then
        assertThat(token.isUsable(token.getExpiresAt().plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("[F-02][PW-02] 해시가 64자가 아니면 발급할 수 없다")
    void rejectsMalformedHash() {
        assertThatThrownBy(() -> PasswordResetToken.issue(1L, "short", clock.instant()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
