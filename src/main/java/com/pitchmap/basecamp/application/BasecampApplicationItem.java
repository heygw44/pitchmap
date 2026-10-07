package com.pitchmap.basecamp.application;

import java.time.Instant;

/**
 * 캠프 리더가 보는 합류 신청 한 건이다. 이메일과 출생연도는 담지 않는다.
 *
 * @param status 신청 상태 이름
 * @param message 신청자가 남긴 메시지. 없으면 null
 */
public record BasecampApplicationItem(
        long applicationId, String status, String message, Instant appliedAt, Applicant applicant) {

    /**
     * 신청자의 프로필이다. 연령대·성별은 본인확인한 값이 있으면 그 값(verified true), 없으면 직접 밝힌 값(verified false, 밝히지 않았으면 null)이다.
     */
    public record Applicant(
            long memberId,
            String nickname,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions) {}
}
