package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemberTest {

    private static final String HASH = "$2a$10$" + "a".repeat(53);
    private static final Email EMAIL = Email.of("User@Example.com");
    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Test
    @DisplayName("[F-01] 가입하면 미인증 일반 회원이고 시각은 now이다")
    void register_setsInitialState() {
        Member member = Member.register(EMAIL, HASH, "nick", NOW);

        assertThat(member.getId()).isNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.UNVERIFIED);
        assertThat(member.getRole()).isEqualTo(MemberRole.USER);
        assertThat(member.getCreatedAt()).isEqualTo(NOW);
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
        assertThat(member.getSelfAgeGroup()).isNull();
        assertThat(member.getSelfGender()).isNull();
        assertThat(member.getEmailVerifiedAt()).isNull();
        assertThat(member.getSuspendedUntil()).isNull();
        assertThat(member.getWithdrawnAt()).isNull();
        assertThat(member.getPasswordHash()).isEqualTo(HASH);
    }

    @Test
    @DisplayName("[F-01][ID-01] 이메일은 소문자 문자열로 저장한다")
    void register_storesLowercaseEmail() {
        assertThat(Member.register(EMAIL, HASH, "nick", NOW).getEmail()).isEqualTo("user@example.com");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 20})
    @DisplayName("[F-01] 닉네임이 2~20자이면 허용한다")
    void register_acceptsNicknameBoundary(int length) {
        String nickname = "n".repeat(length);

        assertThat(Member.register(EMAIL, HASH, nickname, NOW).getNickname()).isEqualTo(nickname);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 21})
    @DisplayName("[F-01] 닉네임이 범위를 벗어나면 거부한다")
    void register_rejectsNicknameOutOfRange(int length) {
        String nickname = "n".repeat(length);

        assertThatThrownBy(() -> Member.register(EMAIL, HASH, nickname, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "   \t"})
    @DisplayName("[F-01] 닉네임이 공백이면 거부한다")
    void register_rejectsBlankNickname(String nickname) {
        assertThatThrownBy(() -> Member.register(EMAIL, HASH, nickname, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("[F-01] 비밀번호 해시가 비어 있으면 거부한다")
    void register_rejectsBlankHash(String hash) {
        assertThatThrownBy(() -> Member.register(EMAIL, hash, "nick", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-01] null 인자는 거부한다")
    void register_rejectsNulls() {
        assertThatThrownBy(() -> Member.register(null, HASH, "nick", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.register(EMAIL, null, "nick", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.register(EMAIL, HASH, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.register(EMAIL, HASH, "nick", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
