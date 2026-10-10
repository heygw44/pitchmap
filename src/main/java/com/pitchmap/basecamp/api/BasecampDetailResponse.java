package com.pitchmap.basecamp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.basecamp.application.BasecampDetail;
import com.pitchmap.basecamp.application.JoinConditionSummary;
import com.pitchmap.basecamp.domain.BasecampRelation;
import java.time.LocalDate;
import java.util.List;

/**
 * 베이스캠프 상세 응답이다. 요청자가 로그인하지 않았으면 멤버의 닉네임과 역할만, 로그인했으면 프로필 요약까지 담는다.
 * 권한이 없는 필드는 null로 두지 않고 필드 자체를 뺀다. 이메일과 출생연도는 어떤 경우에도 담지 않는다.
 */
public sealed interface BasecampDetailResponse {

    /** 모든 요청자에게 보여 주는 안전 안내다. */
    String SAFETY_NOTICE = "출발 전 일정을 가족이나 지인과 공유하세요. 플랫폼 밖 송금 요구는 신고해 주세요.";

    static BasecampDetailResponse from(BasecampDetail detail, boolean loggedIn) {
        SpotResponse spot = new SpotResponse(
                detail.spot().spotId(), detail.spot().name(), detail.spot().type());
        if (!loggedIn) {
            return anonymous(detail, spot);
        }
        List<MemberEntry> members =
                detail.members().stream().map(MemberEntry::from).toList();
        MemberEntry leader = MemberEntry.from(detail.leader());
        return new Member(
                detail.basecampId(),
                detail.title(),
                detail.description(),
                spot,
                detail.startDate(),
                detail.endDate(),
                detail.capacity(),
                detail.headcount(),
                detail.status(),
                detail.joinCondition(),
                new LeaderEntry(leader.memberId(), leader.nickname(), leader.trustLevel()),
                members,
                detail.myRelation(),
                detail.contactInfo(),
                SAFETY_NOTICE,
                detail.eligibility() == null ? null : detail.eligibility().canApply(),
                detail.eligibility() == null
                        ? null
                        : detail.eligibility().unmetReasons().stream()
                                .map(Enum::name)
                                .toList());
    }

    private static Anonymous anonymous(BasecampDetail detail, SpotResponse spot) {
        List<AnonymousMemberEntry> members = detail.members().stream()
                .map(member -> new AnonymousMemberEntry(member.memberId(), member.nickname(), member.role()))
                .toList();
        return new Anonymous(
                detail.basecampId(),
                detail.title(),
                detail.description(),
                spot,
                detail.startDate(),
                detail.endDate(),
                detail.capacity(),
                detail.headcount(),
                detail.status(),
                detail.joinCondition(),
                new AnonymousLeader(detail.leader().memberId(), detail.leader().nickname()),
                members,
                BasecampRelation.NONE,
                SAFETY_NOTICE);
    }

    /** 비로그인 요청자가 보는 상세다. 멤버는 닉네임과 역할만 있고, 연락 수단은 없다. */
    record Anonymous(
            long basecampId,
            String title,
            String description,
            SpotResponse spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            String status,
            JoinConditionSummary joinCondition,
            AnonymousLeader leader,
            List<AnonymousMemberEntry> members,
            BasecampRelation myRelation,
            String safetyNotice)
            implements BasecampDetailResponse {}

    /**
     * 로그인한 요청자가 보는 상세다. contactInfo는 등록된 값이 있고, 요청자가 확정된 베이스캠프의 멤버이거나
     * 확정 전 베이스캠프의 캠프 리더일 때만 응답에 나타나고, 아니면 필드 자체가 없다. canApply와 unmetReasons는 모집 중인 베이스캠프일 때만 나타난다.
     */
    record Member(
            long basecampId,
            String title,
            String description,
            SpotResponse spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            String status,
            JoinConditionSummary joinCondition,
            LeaderEntry leader,
            List<MemberEntry> members,
            BasecampRelation myRelation,
            @JsonInclude(JsonInclude.Include.NON_NULL) String contactInfo,
            String safetyNotice,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean canApply,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> unmetReasons)
            implements BasecampDetailResponse {}

    record SpotResponse(long spotId, String name, String type) {}

    record AnonymousLeader(long memberId, String nickname) {}

    record AnonymousMemberEntry(long memberId, String nickname, String role) {}

    record LeaderEntry(long memberId, String nickname, int trustLevel) {}

    /** 로그인한 요청자가 보는 멤버다. 연령대·성별이 없으면 null이고, 본인확인한 값이면 verified가 true다. */
    record MemberEntry(
            long memberId,
            String nickname,
            String role,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions) {

        static MemberEntry from(BasecampDetail.DetailMember member) {
            return new MemberEntry(
                    member.memberId(),
                    member.nickname(),
                    member.role(),
                    member.ageGroup(),
                    member.ageGroupVerified(),
                    member.gender(),
                    member.genderVerified(),
                    member.trustLevel(),
                    member.completedCompanions());
        }
    }
}
