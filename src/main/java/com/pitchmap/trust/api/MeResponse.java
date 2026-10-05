package com.pitchmap.trust.api;

import com.pitchmap.member.application.MyInfo;
import com.pitchmap.trust.application.TrustSummary;

/**
 * 내 정보 응답이다. 회원 모듈이 가진 계정·자기 신고 정보와 신뢰 모듈이 계산한 본인확인 여부·신뢰 단계를 한 번에 담는다.
 * 자기 신고 값({@code selfAgeGroup}, {@code selfGender})을 입력하지 않았으면 서버는 JSON {@code null}로 응답한다.
 */
public record MeResponse(
        long memberId,
        String email,
        String nickname,
        String status,
        String role,
        String selfAgeGroup,
        String selfGender,
        boolean identityVerified,
        int trustLevel) {

    public static MeResponse of(MyInfo myInfo, TrustSummary trustSummary) {
        return new MeResponse(
                myInfo.memberId(),
                myInfo.email(),
                myInfo.nickname(),
                myInfo.status(),
                myInfo.role(),
                myInfo.selfAgeGroup(),
                myInfo.selfGender(),
                trustSummary.identityVerified(),
                trustSummary.trustLevel());
    }

    // 레코드 기본 toString은 모든 구성요소를 찍는다. 그래서 이메일이 로그에 새지 않도록 우리가 재정의했다.
    @Override
    public String toString() {
        return "MeResponse[memberId=" + memberId + ", nickname=" + nickname + ", status=" + status + ", role=" + role
                + ", selfAgeGroup=" + selfAgeGroup + ", selfGender=" + selfGender + ", identityVerified="
                + identityVerified + ", trustLevel=" + trustLevel + "]";
    }
}
