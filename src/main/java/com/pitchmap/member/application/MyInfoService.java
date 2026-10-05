package com.pitchmap.member.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.web.PatchField;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.domain.MemberRepository;
import com.pitchmap.member.domain.SelfAgeGroup;
import com.pitchmap.member.domain.SelfGender;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyInfoService {

    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MyInfo find(long memberId) {
        return MyInfo.from(loadMember(memberId));
    }

    /** 호출하면 요청에 있는 필드만 바꾸고 바뀐 뒤의 내 정보를 돌려준다. 요청에 없는 필드는 그대로 둔다. */
    @Transactional
    public MyInfo update(long memberId, MyInfoUpdateCommand command) {
        Member member = loadMember(memberId);
        Instant now = Instant.now(clock);
        if (command.nickname().present()) {
            changeNickname(member, command.nickname().value(), now);
        }
        PatchField<String> selfAgeGroup = command.selfAgeGroup();
        if (selfAgeGroup.present()) {
            member.changeSelfAgeGroup(parseSelfAgeGroup(selfAgeGroup.value()), now);
        }
        PatchField<String> selfGender = command.selfGender();
        if (selfGender.present()) {
            member.changeSelfGender(parseSelfGender(selfGender.value()), now);
        }
        flushOrTranslate();
        return MyInfo.from(member);
    }

    private Member loadMember(long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    // 닉네임은 회원마다 꼭 있어야 하는 값이라 null로 지울 수 없다.
    // 지금 쓰는 닉네임을 그대로 보내면 바꿀 것이 없으므로, 서비스는 중복 검사도 하지 않고 그대로 성공시킨다.
    private void changeNickname(Member member, String nickname, Instant now) {
        if (nickname == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "닉네임은 지울 수 없습니다.");
        }
        if (nickname.equals(member.getNickname())) {
            return;
        }
        // DB가 닉네임을 대소문자 구분 없이 비교하므로, 자기 닉네임의 대소문자만 바꾸는 요청도 중복으로 잡지 않게 본인 행을 뺀다.
        if (memberRepository.existsByNicknameAndIdNot(nickname, member.getId())) {
            throw new MemberException(MemberErrorCode.MEMBER_NICKNAME_DUPLICATED);
        }
        member.changeNickname(nickname, now);
    }

    private static SelfAgeGroup parseSelfAgeGroup(String name) {
        if (name == null) {
            return null;
        }
        return SelfAgeGroup.parse(name);
    }

    private static SelfGender parseSelfGender(String name) {
        if (name == null) {
            return null;
        }
        return SelfGender.parse(name);
    }

    // 중복 검사 뒤 커밋 전에 다른 회원이 같은 닉네임으로 먼저 바꿀 수 있다.
    // 이 위반이 커밋 때 드러나면 서비스가 오류 코드로 바꿀 수 없어서 500이 나간다.
    // 그래서 서비스는 트랜잭션 안에서 직접 flush해 위반을 여기서 드러내고 닉네임 중복 오류로 바꾼다.
    private void flushOrTranslate() {
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw MemberUniqueConstraintTranslator.translate(e);
        }
    }
}
