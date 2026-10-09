package com.pitchmap.member.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberRepository;
import com.pitchmap.member.domain.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final MemberRepository memberRepository;

    /**
     * 호출하면 회원의 공개 가능한 정보(닉네임과 자기 신고 연령대·성별)를 돌려준다.
     * 없는 회원이거나 탈퇴한 회원이면 존재를 드러내지 않도록 {@code NOT_FOUND}로 실패한다. 미인증·정지 회원은 조회할 수 있다.
     */
    @Transactional(readOnly = true)
    public MemberProfile find(long memberId) {
        Member member = memberRepository
                .findById(memberId)
                .filter(found -> found.getStatus() != MemberStatus.WITHDRAWN)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return MemberProfile.from(member);
    }

    /**
     * 호출하면 탈퇴한 회원을 포함해 회원의 공개 가능한 정보를 돌려준다. 탈퇴한 회원의 닉네임은 이미 {@code 탈퇴회원_{id}}로 바뀌어 있고
     * 자기 신고 값은 비어 있다. 베이스캠프 멤버 목록처럼 다른 화면 안에 회원이 끼어 보이는 곳에서 쓴다. 회원 프로필 화면처럼
     * 탈퇴한 회원이 드러나면 안 되는 곳은 {@link #find}를 쓴다. 없는 회원이면 {@code NOT_FOUND}로 실패한다.
     */
    @Transactional(readOnly = true)
    public MemberProfile findIncludingWithdrawn(long memberId) {
        Member member =
                memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return MemberProfile.from(member);
    }
}
