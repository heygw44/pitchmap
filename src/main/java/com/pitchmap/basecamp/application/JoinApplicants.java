package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.pitchmap.basecamp.domain.JoinGender;
import com.pitchmap.trust.application.TrustDetail;

/** 신뢰 모듈이 준 회원 정보를 합류 자격 판정이 쓰는 {@link Applicant}로 바꾼다. 검색과 합류 신청이 같은 변환을 쓴다. */
final class JoinApplicants {

    private JoinApplicants() {}

    // 신뢰 모듈의 연령대는 이름 문자열로 오고, 그 모듈의 enum을 참조할 수 없다. 그래서 합류 조건이 쓰는 십 단위 숫자로 여기서 바꾼다.
    static Applicant from(TrustDetail trust) {
        Integer ageGroup = trust.verifiedAgeGroup() == null ? null : toAgeGroupNumber(trust.verifiedAgeGroup());
        JoinGender gender = trust.verifiedGender() == null ? null : JoinGender.valueOf(trust.verifiedGender());
        return new Applicant(trust.trustLevel(), ageGroup, gender);
    }

    private static int toAgeGroupNumber(String ageGroup) {
        return switch (ageGroup) {
            case "TWENTIES" -> 20;
            case "THIRTIES" -> 30;
            case "FORTIES" -> 40;
            case "FIFTIES" -> 50;
            case "SIXTIES_PLUS" -> 60;
            default -> throw new IllegalStateException("알 수 없는 연령대입니다: " + ageGroup);
        };
    }
}
