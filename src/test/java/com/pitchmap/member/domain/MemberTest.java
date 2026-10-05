package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberTest {

    private static final String HASH = "$2a$10$" + "a".repeat(53);
    private static final Email EMAIL = Email.of("User@Example.com");
    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant LATER = NOW.plus(Duration.ofMinutes(5));

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

    @ParameterizedTest
    @ValueSource(ints = {2, 20})
    @DisplayName("닉네임을 2~20자로 바꾸면 닉네임과 수정 시각이 바뀐다")
    void changeNickname_acceptsBoundaryAndUpdatesUpdatedAt(int length) {
        // given
        Member member = Member.register(EMAIL, HASH, "nick", NOW);
        String nickname = "m".repeat(length);

        // when
        member.changeNickname(nickname, LATER);

        // then
        assertThat(member.getNickname()).isEqualTo(nickname);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
        assertThat(member.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"m", "mmmmmmmmmmmmmmmmmmmmm", "", "  ", "   \t"})
    @DisplayName("닉네임을 1자·21자·공백·null로 바꾸려 하면 INVALID_INPUT이고 닉네임과 수정 시각은 그대로다")
    void changeNickname_rejectsInvalidNickname(String nickname) {
        // given
        Member member = Member.register(EMAIL, HASH, "nick", NOW);

        // when & then
        assertThatThrownBy(() -> member.changeNickname(nickname, LATER))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThat(member.getNickname()).isEqualTo("nick");
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @EnumSource(SelfAgeGroup.class)
    @DisplayName("자기 신고 연령대를 정하면 그 값과 수정 시각이 바뀐다")
    void changeSelfAgeGroup_setsValue(SelfAgeGroup selfAgeGroup) {
        // given
        Member member = Member.register(EMAIL, HASH, "nick", NOW);

        // when
        member.changeSelfAgeGroup(selfAgeGroup, LATER);

        // then
        assertThat(member.getSelfAgeGroup()).isEqualTo(selfAgeGroup);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("자기 신고 연령대에 null을 넘기면 값을 지운다")
    void changeSelfAgeGroup_clearsWithNull() {
        // given
        Member member = Member.register(EMAIL, HASH, "nick", NOW);
        member.changeSelfAgeGroup(SelfAgeGroup.THIRTIES, NOW);

        // when
        member.changeSelfAgeGroup(null, LATER);

        // then
        assertThat(member.getSelfAgeGroup()).isNull();
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @ParameterizedTest
    @EnumSource(SelfGender.class)
    @DisplayName("자기 신고 성별을 정하면 그 값과 수정 시각이 바뀐다")
    void changeSelfGender_setsValue(SelfGender selfGender) {
        // given
        Member member = Member.register(EMAIL, HASH, "nick", NOW);

        // when
        member.changeSelfGender(selfGender, LATER);

        // then
        assertThat(member.getSelfGender()).isEqualTo(selfGender);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("자기 신고 성별에 null을 넘기면 값을 지운다")
    void changeSelfGender_clearsWithNull() {
        // given
        Member member = Member.register(EMAIL, HASH, "nick", NOW);
        member.changeSelfGender(SelfGender.FEMALE, NOW);

        // when
        member.changeSelfGender(null, LATER);

        // then
        assertThat(member.getSelfGender()).isNull();
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("수정 시각이 null이면 IllegalArgumentException이다")
    void changeMethods_rejectNullNow() {
        // given
        Member member = Member.register(EMAIL, HASH, "nick", NOW);

        // when & then
        assertThatThrownBy(() -> member.changeNickname("nick2", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.changeSelfAgeGroup(SelfAgeGroup.TWENTIES, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> member.changeSelfGender(SelfGender.MALE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
