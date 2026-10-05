package com.pitchmap.member.domain;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberChangePasswordTest {

    private static final String NEW_HASH = "$2a$10$" + "b".repeat(53);

    @Test
    @DisplayName("[F-02][PW-02] 비밀번호를 바꾸면 해시와 수정 시각이 바뀐다")
    void replacesHashAndUpdatedAt() {
        // given
        Member member = aMember().build();
        Instant later = MutableClock.DEFAULT_INSTANT.plus(Duration.ofMinutes(5));

        // when
        member.changePassword(NEW_HASH, later);

        // then
        assertThat(member.getPasswordHash()).isEqualTo(NEW_HASH);
        assertThat(member.getUpdatedAt()).isEqualTo(later);
        assertThat(member.getCreatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-02][PW-06] 비밀번호를 바꾸면 바꾼 시각이 기록되고, 가입 직후에는 비어 있다")
    void recordsPasswordChangedAt() {
        // given
        Member member = aMember().build();
        Instant later = MutableClock.DEFAULT_INSTANT.plus(Duration.ofMinutes(5));
        assertThat(member.getPasswordChangedAt()).isNull();

        // when
        member.changePassword(NEW_HASH, later);

        // then
        assertThat(member.getPasswordChangedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("[F-02][PW-02] 비밀번호를 바꿔도 상태와 이메일과 인증 시각은 그대로다")
    void keepsStatusAndEmail() {
        // given
        Member member = aMember().email("keep@example.com").build();
        MemberStatus status = member.getStatus();

        // when
        member.changePassword(NEW_HASH, MutableClock.DEFAULT_INSTANT.plusSeconds(1));

        // then
        assertThat(member.getStatus()).isEqualTo(status);
        assertThat(member.getEmail()).isEqualTo("keep@example.com");
        assertThat(member.getEmailVerifiedAt()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("[F-02][PW-02] 해시가 null이거나 비어 있으면 거부하고 기존 해시를 유지한다")
    void rejectsBlankHash(String blank) {
        // given
        Member member = aMember().build();
        String before = member.getPasswordHash();

        // then
        assertThatThrownBy(() -> member.changePassword(blank, MutableClock.DEFAULT_INSTANT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(member.getPasswordHash()).isEqualTo(before);
    }

    @Test
    @DisplayName("[F-02][PW-06] 변경이 거부되면 바꾼 시각도 그대로다")
    void keepsPasswordChangedAtWhenRejected() {
        // given
        Member member = aMember().build();

        // then
        assertThatThrownBy(() -> member.changePassword(" ", MutableClock.DEFAULT_INSTANT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(member.getPasswordChangedAt()).isNull();
    }
}
