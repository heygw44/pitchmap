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
}
