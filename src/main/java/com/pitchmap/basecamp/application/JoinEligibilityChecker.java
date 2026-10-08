package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampErrorCode;
import com.pitchmap.basecamp.domain.BasecampException;
import com.pitchmap.basecamp.domain.JoinEligibilityPolicy;
import com.pitchmap.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.pitchmap.basecamp.domain.JoinUnmetReason;
import com.pitchmap.basecamp.domain.PriorRelation;
import com.pitchmap.basecamp.infra.BasecampSearchMapper;
import com.pitchmap.basecamp.infra.ConfirmedScheduleRow;
import com.pitchmap.trust.application.TrustSummaryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 회원이 베이스캠프의 합류 조건과 일정을 충족하는지 검사한다. 합류 신청과 신청 승인이 같은 검사를 쓴다.
 * 신청할 때 통과했더라도 승인할 때까지 신뢰 단계나 확정된 일정이 바뀔 수 있어서, 승인할 때 같은 규칙으로 다시 검사한다.
 */
@Component
@RequiredArgsConstructor
class JoinEligibilityChecker {

    private final TrustSummaryService trustSummaryService;
    private final BasecampSearchMapper basecampSearchMapper;

    /** 호출하면 memberId인 회원의 신뢰 정보를 읽어 자격 판정에 쓰는 값으로 돌려준다. */
    Applicant applicantOf(long memberId) {
        return JoinApplicants.from(trustSummaryService.detail(memberId));
    }

    /**
     * 호출하면 applicant가 basecamp의 합류 조건과 일정을 충족하는지 검사하고, 충족하지 못하면 첫 번째 이유의 오류로 거부한다.
     * 최소 신뢰 단계, 연령대, 성별을 충족하지 못하면 BASECAMP_CONDITION_NOT_MET이고, 같은 기간에 확정된 다른 베이스캠프의
     * 멤버이면 BASECAMP_DATE_CONFLICT이다.
     *
     * <p>이전 관계(이미 멤버이거나 거절·탈퇴·강퇴된 적)는 도메인의 신청·승인 검사가 먼저 다루므로 NONE으로 넘긴다.
     */
    void requireEligible(Basecamp basecamp, Applicant applicant, long memberId) {
        boolean dateConflict = hasDateConflict(basecamp, memberId);
        List<JoinUnmetReason> reasons = JoinEligibilityPolicy.unmetReasons(
                basecamp.getJoinCondition(), applicant, dateConflict, PriorRelation.NONE);
        if (!reasons.isEmpty()) {
            throw toException(reasons.get(0));
        }
    }

    /**
     * 호출하면 viewerId인 회원이 basecamp에 신청할 수 있는지와, 없다면 모든 이유를 순서대로 돌려준다. 이미 멤버이거나 대기 중인 신청이 있으면
     * ALREADY_JOINED, 거절·탈퇴·강퇴된 적이 있으면 REAPPLY_NOT_ALLOWED가 이유에 들어간다.
     */
    JoinEligibility judge(Basecamp basecamp, long viewerId) {
        Applicant applicant = applicantOf(viewerId);
        PriorRelation prior = basecampSearchMapper.selectViewerHistory(viewerId, List.of(basecamp.getId())).stream()
                .findFirst()
                .map(history -> PriorRelation.of(history.memberStatus(), history.applicationStatus()))
                .orElse(PriorRelation.NONE);
        return JoinEligibility.of(JoinEligibilityPolicy.unmetReasons(
                basecamp.getJoinCondition(), applicant, hasDateConflict(basecamp, viewerId), prior));
    }

    // 확정된 일정과 하루라도 겹치면 충돌이다. 이 베이스캠프 자신은 비교에서 뺀다.
    private boolean hasDateConflict(Basecamp basecamp, long memberId) {
        List<ConfirmedScheduleRow> schedules = basecampSearchMapper.selectConfirmedSchedules(memberId);
        return schedules.stream()
                .filter(schedule -> schedule.basecampId() != basecamp.getId())
                .anyMatch(schedule -> JoinEligibilityPolicy.overlaps(
                        basecamp.getStartDate(), basecamp.getEndDate(), schedule.startDate(), schedule.endDate()));
    }

    private static BasecampException toException(JoinUnmetReason reason) {
        return switch (reason) {
            case TRUST_LEVEL, AGE_GROUP, GENDER -> new BasecampException(BasecampErrorCode.BASECAMP_CONDITION_NOT_MET);
            case DATE_CONFLICT -> new BasecampException(BasecampErrorCode.BASECAMP_DATE_CONFLICT);
            case ALREADY_JOINED -> new BasecampException(BasecampErrorCode.BASECAMP_ALREADY_APPLIED);
            case REAPPLY_NOT_ALLOWED -> new BasecampException(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED);
        };
    }
}
