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
    @DisplayName("[F-01] 닉네임이 범위를 벗어나면 INVALID_INPUT으로 거부한다")
    void register_rejectsNicknameOutOfRange(int length) {
        String nickname = "n".repeat(length);

        assertInvalidInput(() -> Member.register(EMAIL, HASH, nickname, NOW));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "   \t"})
    @DisplayName("[F-01] 닉네임이 null이거나 공백이면 INVALID_INPUT으로 거부한다")
    void register_rejectsBlankNickname(String nickname) {
        assertInvalidInput(() -> Member.register(EMAIL, HASH, nickname, NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {" nick", "nick ", " nick ", "\tnick", "nick\n", "ni ck ", "ni  ck"})
    @DisplayName("[F-01] 닉네임 앞뒤에 공백이 있거나 공백이 연속하면 INVALID_INPUT으로 거부한다")
    void register_rejectsMisplacedSpaces(String nickname) {
        assertInvalidInput(() -> Member.register(EMAIL, HASH, nickname, NOW));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // 공백류: NBSP, 숫자 크기 공백, 좁은 NBSP, 전각 공백, 줄 구분자, 문단 구분자
                "nick\u00A0",
                "ni\u00A0ck",
                "ni\u2007ck",
                "ni\u202Fck",
                "ni\u3000ck",
                "ni\u2028ck",
                "ni\u2029ck",
                // 서식 문자: 폭 없는 공백, ZWNJ, ZWJ, 단어 결합자, BOM, 소프트 하이픈, 오른쪽에서 왼쪽 덮어쓰기
                "ni\u200Bck",
                "\u200B\u200B",
                "ni\u200Cck",
                "ni\u200Dck",
                "ni\u2060ck",
                "\uFEFFnick",
                "ni\u00ADck",
                "\u202Enick",
                // 제어 문자
                "ni\u0000ck",
                "ni\u0085ck",
                // 한글 채움 문자
                "nick\u3164",
                "\u3164\u3164",
                "ni\u115Fck",
                "ni\u1160ck",
                "ni\uFFA0ck",
                // ZWJ로 이은 이모지
                "👨\u200D👩\u200D👧"
            })
    @DisplayName("[F-01] 닉네임에 공백류·제어·서식 문자나 한글 채움 문자가 있으면 INVALID_INPUT으로 거부한다")
    void register_rejectsHiddenCharacters(String nickname) {
        assertInvalidInput(() -> Member.register(EMAIL, HASH, nickname, NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"백패커 민수", "hi ker", "하이커❤\uFE0F", "굿👍\uD83C\uDFFB"})
    @DisplayName("[F-01] 닉네임 가운데의 일반 공백 한 칸과 이모지의 변이 선택자·피부색 수정자는 허용한다")
    void register_acceptsInnerSpaceAndEmojiModifiers(String nickname) {
        assertThat(Member.register(EMAIL, HASH, nickname, NOW).getNickname()).isEqualTo(nickname);
    }

    @Test
    @DisplayName("[F-01] 닉네임 길이는 UTF-16 문자 수가 아니라 실제 글자 수로 센다")
    void register_countsNicknameLengthInCodePoints() {
        String twentyEmoji = "😀".repeat(20);

        assertThat(Member.register(EMAIL, HASH, twentyEmoji, NOW).getNickname()).isEqualTo(twentyEmoji);
        assertInvalidInput(() -> Member.register(EMAIL, HASH, "😀".repeat(21), NOW));
        assertInvalidInput(() -> Member.register(EMAIL, HASH, "😀", NOW));
    }

    @ParameterizedTest
    @ValueSource(strings = {" nick", "nick ", "ni  ck", "nick\u00A0", "nick\u3164", "ni\u200Bck"})
    @DisplayName("닉네임을 앞뒤·연속 공백이나 보이지 않는 문자가 든 값으로 바꾸려 하면 INVALID_INPUT이고 닉네임과 수정 시각은 그대로다")
    void changeNickname_rejectsMisplacedSpacesAndHiddenCharacters(String nickname) {
        Member member = Member.register(EMAIL, HASH, "nick", NOW);

        assertInvalidInput(() -> member.changeNickname(nickname, LATER));

        assertThat(member.getNickname()).isEqualTo("nick");
        assertThat(member.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("[F-01] 비밀번호 해시가 비어 있으면 거부한다")
    void register_rejectsBlankHash(String hash) {
        assertThatThrownBy(() -> Member.register(EMAIL, hash, "nick", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-01] 이메일, 비밀번호 해시, 시각이 null이면 거부한다")
    void register_rejectsNulls() {
        assertThatThrownBy(() -> Member.register(null, HASH, "nick", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Member.register(EMAIL, null, "nick", NOW))
                .isInstanceOf(IllegalArgumentException.class);
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
        assertInvalidInput(() -> member.changeNickname(nickname, LATER));
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

    private static void assertInvalidInput(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }
}
