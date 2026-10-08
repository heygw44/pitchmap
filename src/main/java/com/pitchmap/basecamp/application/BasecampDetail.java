package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.BasecampRelation;
import java.time.LocalDate;
import java.util.List;

/**
 * 베이스캠프 상세다. leader는 캠프 리더의 프로필이고, 리더의 멤버 행이 ACTIVE가 아니어도 채운다. members는 ACTIVE 멤버를 캠프 리더 먼저, 그다음 합류한 순서로 담고, headcount는 그 수와 같다.
 *
 * <p>myRelation은 요청자와 이 베이스캠프의 관계다. contactInfo는 요청자가 볼 수 있고 등록된 값이 있을 때만 채우고, 아니면 null이다.
 * eligibility는 로그인한 요청자가 모집 중인 베이스캠프를 볼 때만 채우고, 아니면 null이다.
 */
public record BasecampDetail(
        long basecampId,
        String title,
        String description,
        SpotSummary spot,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int headcount,
        String status,
        JoinConditionSummary joinCondition,
        DetailMember leader,
        List<DetailMember> members,
        BasecampRelation myRelation,
        String contactInfo,
        JoinEligibility eligibility) {

    /** 장소 요약이다. type은 장소 유형 이름이다. */
    public record SpotSummary(long spotId, String name, String type) {}

    /**
     * 멤버의 닉네임과 프로필이다. 연령대·성별은 본인확인한 값이 있으면 그 값(verified true), 없으면 회원이 직접 밝힌 값(없으면 null)이다.
     * role은 LEADER 또는 MEMBER다.
     */
    public record DetailMember(
            long memberId,
            String nickname,
            String role,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions) {}
}
