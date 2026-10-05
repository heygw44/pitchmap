package com.pitchmap.member.domain;

import static com.pitchmap.member.domain.PasswordResetRequestPolicy.emailKey;
import static com.pitchmap.member.domain.PasswordResetRequestPolicy.ipKey;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordResetRequestPolicyTest {

    @Test
    @DisplayName("[F-02][PW-05] 이메일 규칙은 간격 60초, 구간 24시간, 5번이다")
    void emailRuleValues() {
        assertThat(PasswordResetRequestPolicy.EMAIL.minInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(PasswordResetRequestPolicy.EMAIL.window()).isEqualTo(Duration.ofHours(24));
        assertThat(PasswordResetRequestPolicy.EMAIL.maxRequests()).isEqualTo(5);
    }

    @Test
    @DisplayName("[F-02][PW-05] IP 규칙은 간격 없음, 구간 1시간, 20번이다")
    void ipRuleValues() {
        assertThat(PasswordResetRequestPolicy.IP.minInterval()).isEqualTo(Duration.ZERO);
        assertThat(PasswordResetRequestPolicy.IP.window()).isEqualTo(Duration.ofHours(1));
        assertThat(PasswordResetRequestPolicy.IP.maxRequests()).isEqualTo(20);
    }

    @Test
    @DisplayName("[F-02][PW-05] 마지막 요청 뒤 24시간이 지나면 행이 쓸모없다")
    void staleAfterValue() {
        assertThat(PasswordResetRequestPolicy.STALE_AFTER).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("[F-02][PW-05] 키는 소문자 16진수 64자다")
    void keysAreSixtyFourLowercaseHexCharacters() {
        assertThat(emailKey("a@x.com")).matches("[0-9a-f]{64}");
        assertThat(ipKey("203.0.113.7")).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("[F-02][PW-05] 같은 입력은 같은 키를 만들고 다른 입력은 다른 키를 만든다")
    void sameInputGivesSameKey() {
        assertThat(emailKey("a@x.com")).isEqualTo(emailKey("a@x.com"));
        assertThat(ipKey("203.0.113.7")).isEqualTo(ipKey("203.0.113.7"));
        assertThat(emailKey("a@x.com")).isNotEqualTo(emailKey("b@x.com"));
        assertThat(ipKey("203.0.113.7")).isNotEqualTo(ipKey("203.0.113.8"));
    }

    @Test
    @DisplayName("[F-02][PW-05] 접두사가 달라서 같은 값도 이메일 키와 IP 키가 다르다")
    void prefixSeparatesEmailAndIpKeys() {
        assertThat(emailKey("a@x.com")).isNotEqualTo(ipKey("a@x.com"));
    }

    @Test
    @DisplayName("[F-02][PW-05] 키는 접두사를 붙인 값의 SHA-256이다")
    void keysAreHashesOfPrefixedValues() {
        assertThat(emailKey("a@x.com")).isEqualTo(ResetToken.hash("email:a@x.com"));
        assertThat(ipKey("203.0.113.7")).isEqualTo(ResetToken.hash("ip:203.0.113.7"));
    }
}
