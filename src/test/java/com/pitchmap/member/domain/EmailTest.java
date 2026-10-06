package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Test
    @DisplayName("[F-01][ID-01] 대소문자가 섞인 이메일을 소문자로 정규화한다")
    void of_lowercases() {
        Email email = Email.of("Hello.World@Example.COM");

        assertThat(email.value()).isEqualTo("hello.world@example.com");
    }

    @Test
    @DisplayName("[F-01][ID-01] 도메인은 마지막 @ 뒤이다")
    void domain_returnsPartAfterAt() {
        assertThat(Email.of("User@Mail.Example.com").domain()).isEqualTo("mail.example.com");
    }

    @Test
    @DisplayName("[F-01][ID-01] 도메인 후보는 상위 도메인을 라벨 2개 이상까지 포함한다")
    void domainCandidates_includesParents() {
        assertThat(Email.of("x@a.b.mailinator.com").domainCandidates())
                .containsExactly("a.b.mailinator.com", "b.mailinator.com", "mailinator.com");
    }

    @Test
    @DisplayName("[F-01][ID-01] 라벨이 2개인 도메인은 자기 자신만 후보이다")
    void domainCandidates_twoLabels() {
        assertThat(Email.of("x@mailinator.com").domainCandidates()).containsExactly("mailinator.com");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"plain", "@example.com", "user@", "user@localhost", "user@[127.0.0.1]", "user@[IPv6:::1]"})
    @DisplayName("[F-01][EV-06] 형식이 잘못됐거나 도메인에 점이 없거나 IP 주소인 값은 IllegalArgumentException이다")
    void of_rejectsInvalid(String raw) {
        assertThatThrownBy(() -> Email.of(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-01][ID-01] 대소문자만 다른 이메일은 같다")
    void equals_isCaseInsensitive() {
        Email lower = Email.of("user@example.com");
        Email mixed = Email.of("User@Example.COM");

        assertThat(mixed).isEqualTo(lower).hasSameHashCodeAs(lower);
    }

    @Test
    @DisplayName("[F-01][ID-01] toString은 주소를 노출하지 않는다")
    void toString_hidesAddress() {
        assertThat(Email.of("user@example.com").toString())
                .doesNotContain("user")
                .doesNotContain("example");
    }
}
