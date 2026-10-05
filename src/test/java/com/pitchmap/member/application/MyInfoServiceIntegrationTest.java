package com.pitchmap.member.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.ErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.common.web.PatchField;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.SelfAgeGroup;
import com.pitchmap.member.domain.SelfGender;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class MyInfoServiceIntegrationTest {

    @Autowired
    MyInfoService myInfoService;

    @Autowired
    MemberJpaRepository memberJpaRepository;

    @Autowired
    MutableClock clock;

    @Test
    @DisplayName("내 정보를 조회하면 회원 ID, 이메일, 닉네임, 상태, 권한, 자기 신고 값을 모두 돌려준다")
    void findReturnsAllFields() {
        // given
        Member member = aMember().email("Me@Example.com").nickname("camper").build();
        member.changeSelfAgeGroup(SelfAgeGroup.THIRTIES, MutableClock.DEFAULT_INSTANT);
        member.changeSelfGender(SelfGender.FEMALE, MutableClock.DEFAULT_INSTANT);
        Member saved = memberJpaRepository.saveAndFlush(member);

        // when
        MyInfo info = myInfoService.find(saved.getId());

        // then
        assertThat(info)
                .isEqualTo(new MyInfo(
                        saved.getId(), "me@example.com", "camper", "UNVERIFIED", "USER", "THIRTIES", "FEMALE"));
    }

    @Test
    @DisplayName("자기 신고 값을 밝히지 않은 회원을 조회하면 자기 신고 값이 null이다")
    void findReturnsNullSelfFieldsWhenUnset() {
        // given
        Member saved = memberJpaRepository.saveAndFlush(aMember().build());

        // when
        MyInfo info = myInfoService.find(saved.getId());

        // then
        assertThat(info.selfAgeGroup()).isNull();
        assertThat(info.selfGender()).isNull();
    }

    @Test
    @DisplayName("없는 회원을 조회하거나 수정하면 NOT_FOUND이다")
    void missingMemberIsNotFound() {
        // given
        long missingId = Long.MAX_VALUE;
        MyInfoUpdateCommand command = command(PatchField.of("newnick"), PatchField.absent(), PatchField.absent());

        // when & then
        assertErrorCode(() -> myInfoService.find(missingId), CommonErrorCode.NOT_FOUND);
        assertErrorCode(() -> myInfoService.update(missingId, command), CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("닉네임과 자기 신고 값을 보내면 모두 바뀌고 수정 시각이 시계 시각이 된다")
    void updateChangesAllFields() {
        // given
        Member saved = memberJpaRepository.saveAndFlush(aMember().build());
        clock.advance(Duration.ofMinutes(5));
        String newNickname = TestSequence.nickname();

        // when
        MyInfo info = myInfoService.update(
                saved.getId(), command(PatchField.of(newNickname), PatchField.of("FORTIES"), PatchField.of("MALE")));

        // then
        assertThat(info.nickname()).isEqualTo(newNickname);
        assertThat(info.selfAgeGroup()).isEqualTo("FORTIES");
        assertThat(info.selfGender()).isEqualTo("MALE");
        Member reloaded = reload(saved);
        assertThat(reloaded.getNickname()).isEqualTo(newNickname);
        assertThat(reloaded.getSelfAgeGroup()).isEqualTo(SelfAgeGroup.FORTIES);
        assertThat(reloaded.getSelfGender()).isEqualTo(SelfGender.MALE);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(Instant.now(clock));
    }

    @Test
    @DisplayName("요청에 없는 필드는 바꾸지 않고 그대로 둔다")
    void absentFieldsAreKept() {
        // given
        Member member = aMember().build();
        member.changeSelfAgeGroup(SelfAgeGroup.TWENTIES, MutableClock.DEFAULT_INSTANT);
        member.changeSelfGender(SelfGender.FEMALE, MutableClock.DEFAULT_INSTANT);
        Member saved = memberJpaRepository.saveAndFlush(member);
        clock.advance(Duration.ofMinutes(5));

        // when
        MyInfo info = myInfoService.update(
                saved.getId(), command(PatchField.absent(), PatchField.absent(), PatchField.absent()));

        // then
        assertThat(info.nickname()).isEqualTo(saved.getNickname());
        assertThat(info.selfAgeGroup()).isEqualTo("TWENTIES");
        assertThat(info.selfGender()).isEqualTo("FEMALE");
        Member reloaded = reload(saved);
        assertThat(reloaded.getSelfAgeGroup()).isEqualTo(SelfAgeGroup.TWENTIES);
        assertThat(reloaded.getSelfGender()).isEqualTo(SelfGender.FEMALE);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("자기 신고 값을 null로 보내면 그 값을 지운다")
    void explicitNullClearsSelfFields() {
        // given
        Member member = aMember().build();
        member.changeSelfAgeGroup(SelfAgeGroup.FIFTIES, MutableClock.DEFAULT_INSTANT);
        member.changeSelfGender(SelfGender.MALE, MutableClock.DEFAULT_INSTANT);
        Member saved = memberJpaRepository.saveAndFlush(member);

        // when
        MyInfo info = myInfoService.update(
                saved.getId(), command(PatchField.absent(), PatchField.of(null), PatchField.of(null)));

        // then
        assertThat(info.selfAgeGroup()).isNull();
        assertThat(info.selfGender()).isNull();
        Member reloaded = reload(saved);
        assertThat(reloaded.getSelfAgeGroup()).isNull();
        assertThat(reloaded.getSelfGender()).isNull();
        assertThat(reloaded.getNickname()).isEqualTo(saved.getNickname());
    }

    @Test
    @DisplayName("닉네임을 null로 보내면 INVALID_INPUT이고 아무것도 바뀌지 않는다")
    void explicitNullNicknameIsRejected() {
        // given
        Member saved = memberJpaRepository.saveAndFlush(aMember().build());
        MyInfoUpdateCommand command = command(PatchField.of(null), PatchField.of("TWENTIES"), PatchField.absent());

        // when & then
        assertErrorCode(() -> myInfoService.update(saved.getId(), command), CommonErrorCode.INVALID_INPUT);
        Member reloaded = reload(saved);
        assertThat(reloaded.getNickname()).isEqualTo(saved.getNickname());
        assertThat(reloaded.getSelfAgeGroup()).isNull();
    }

    @Test
    @DisplayName("닉네임이 1자거나 공백이면 INVALID_INPUT이고 닉네임이 바뀌지 않는다")
    void invalidNicknameIsRejected() {
        // given
        Member saved = memberJpaRepository.saveAndFlush(aMember().build());

        // when & then
        assertErrorCode(
                () -> myInfoService.update(
                        saved.getId(), command(PatchField.of("a"), PatchField.absent(), PatchField.absent())),
                CommonErrorCode.INVALID_INPUT);
        assertErrorCode(
                () -> myInfoService.update(
                        saved.getId(), command(PatchField.of("   "), PatchField.absent(), PatchField.absent())),
                CommonErrorCode.INVALID_INPUT);
        assertThat(reload(saved).getNickname()).isEqualTo(saved.getNickname());
    }

    @Test
    @DisplayName("허용 목록에 없는 연령대나 성별을 보내면 INVALID_INPUT이고 같은 요청의 닉네임 변경도 저장되지 않는다")
    void unknownSelfValueIsRejected() {
        // given
        Member saved = memberJpaRepository.saveAndFlush(aMember().build());
        String newNickname = TestSequence.nickname();

        // when & then
        assertErrorCode(
                () -> myInfoService.update(
                        saved.getId(),
                        command(PatchField.of(newNickname), PatchField.of("TEENS"), PatchField.absent())),
                CommonErrorCode.INVALID_INPUT);
        assertErrorCode(
                () -> myInfoService.update(
                        saved.getId(), command(PatchField.absent(), PatchField.absent(), PatchField.of("OTHER"))),
                CommonErrorCode.INVALID_INPUT);
        Member reloaded = reload(saved);
        assertThat(reloaded.getNickname()).isEqualTo(saved.getNickname());
        assertThat(reloaded.getSelfAgeGroup()).isNull();
        assertThat(reloaded.getSelfGender()).isNull();
    }

    @Test
    @DisplayName("다른 회원이 쓰는 닉네임으로 바꾸면 MEMBER_NICKNAME_DUPLICATED이다")
    void nicknameOfAnotherMemberIsRejected() {
        // given
        Member other =
                memberJpaRepository.saveAndFlush(aMember().nickname("taken").build());
        Member saved = memberJpaRepository.saveAndFlush(aMember().build());

        // when & then
        assertErrorCode(
                () -> myInfoService.update(
                        saved.getId(), command(PatchField.of("taken"), PatchField.absent(), PatchField.absent())),
                MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        assertErrorCode(
                () -> myInfoService.update(
                        saved.getId(), command(PatchField.of("TAKEN"), PatchField.absent(), PatchField.absent())),
                MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        assertThat(reload(saved).getNickname()).isEqualTo(saved.getNickname());
        assertThat(reload(other).getNickname()).isEqualTo("taken");
    }

    @Test
    @DisplayName("지금 쓰는 자기 닉네임을 보내면 바뀌는 것 없이 성공한다")
    void ownCurrentNicknameIsNoOp() {
        // given
        Member saved =
                memberJpaRepository.saveAndFlush(aMember().nickname("mine").build());
        clock.advance(Duration.ofMinutes(5));

        // when
        MyInfo info = myInfoService.update(
                saved.getId(), command(PatchField.of("mine"), PatchField.absent(), PatchField.absent()));

        // then
        assertThat(info.nickname()).isEqualTo("mine");
        assertThat(reload(saved).getUpdatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("자기 닉네임의 대소문자만 바꾸면 중복으로 보지 않고 바꾼다")
    void ownNicknameWithDifferentCaseIsAccepted() {
        // given
        Member saved =
                memberJpaRepository.saveAndFlush(aMember().nickname("mine").build());

        // when
        MyInfo info = myInfoService.update(
                saved.getId(), command(PatchField.of("Mine"), PatchField.absent(), PatchField.absent()));

        // then
        assertThat(info.nickname()).isEqualTo("Mine");
        assertThat(reload(saved).getNickname()).isEqualTo("Mine");
    }

    @Test
    @DisplayName("가장 긴 연령대 값 SIXTIES_PLUS도 저장되고 다시 읽힌다")
    void longestAgeGroupIsPersisted() {
        // given
        Member saved = memberJpaRepository.saveAndFlush(aMember().build());

        // when
        myInfoService.update(
                saved.getId(), command(PatchField.absent(), PatchField.of("SIXTIES_PLUS"), PatchField.absent()));

        // then
        assertThat(reload(saved).getSelfAgeGroup()).isEqualTo(SelfAgeGroup.SIXTIES_PLUS);
        assertThat(myInfoService.find(saved.getId()).selfAgeGroup()).isEqualTo("SIXTIES_PLUS");
    }

    private Member reload(Member member) {
        return memberJpaRepository.findById(member.getId()).orElseThrow();
    }

    private static MyInfoUpdateCommand command(
            PatchField<String> nickname, PatchField<String> selfAgeGroup, PatchField<String> selfGender) {
        return new MyInfoUpdateCommand(nickname, selfAgeGroup, selfGender);
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
