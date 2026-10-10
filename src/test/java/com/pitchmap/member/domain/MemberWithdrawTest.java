package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class MemberWithdrawTest {

    private static final String HASH = "$2a$10$" + "a".repeat(53);
    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant LATER = NOW.plus(Duration.ofMinutes(5));

    @Test
    @DisplayName("[F-01][PV-01][PV-02][PV-03] 탈퇴하면 이메일·비밀번호 해시·자기 신고 값을 지우고 닉네임을 탈퇴회원_{id}로 바꾼다")
    void withdrawErasesPersonalData() {
        Member member = savedMember(12L);
        member.changeSelfAgeGroup(SelfAgeGroup.TWENTIES, NOW);
        member.changeSelfGender(SelfGender.FEMALE, NOW);

        member.withdraw(LATER);

        assertThat(member.getEmail()).isNull();
        assertThat(member.getPasswordHash()).isNull();
        assertThat(member.getSelfAgeGroup()).isNull();
        assertThat(member.getSelfGender()).isNull();
        assertThat(member.getNickname()).isEqualTo("탈퇴회원_12");
        assertThat(member.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(member.getWithdrawnAt()).isEqualTo(LATER);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("[PV-01] 이미 탈퇴한 회원에게 다시 탈퇴를 호출해도 탈퇴 시각이 바뀌지 않는다")
    void withdrawTwiceKeepsFirstWithdrawnAt() {
        Member member = savedMember(12L);
        member.withdraw(LATER);

        member.withdraw(LATER.plus(Duration.ofHours(1)));

        assertThat(member.getWithdrawnAt()).isEqualTo(LATER);
        assertThat(member.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("탈퇴 시각이 null이면 IllegalArgumentException이다")
    void withdrawRejectsNullNow() {
        Member member = savedMember(12L);

        assertThatThrownBy(() -> member.withdraw(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"탈퇴회원_1", "탈퇴회원_12", "탈퇴회원_0123456789"})
    @DisplayName("[PV-02] 탈퇴회원_ 뒤에 숫자만 오는 닉네임은 가입과 닉네임 변경에서 입력 오류다")
    void reservedNicknameIsRejected(String nickname) {
        Member member = savedMember(1L);

        assertThat(Member.isValidNickname(nickname)).isFalse();
        assertInvalidInput(() -> Member.register(Email.of("a@example.com"), HASH, nickname, NOW));
        assertInvalidInput(() -> member.changeNickname(nickname, LATER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"탈퇴회원", "탈퇴회원_", "탈퇴회원_a", "탈퇴회원_1a", "내탈퇴회원_1", "탈퇴회원 1"})
    @DisplayName("[PV-02] 숫자만 이어지지 않는 비슷한 닉네임은 쓸 수 있다")
    void similarNicknameIsAllowed(String nickname) {
        assertThat(Member.isValidNickname(nickname)).isTrue();
    }

    private static Member savedMember(long id) {
        Member member = Member.register(Email.of("User@Example.com"), HASH, "nick", NOW);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static void assertInvalidInput(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }
}
