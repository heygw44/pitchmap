package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.JoinCondition;
import com.pitchmap.basecamp.domain.JoinEligibilityPolicy;
import com.pitchmap.basecamp.domain.JoinEligibilityPolicy.Applicant;
import com.pitchmap.basecamp.domain.JoinGender;
import com.pitchmap.basecamp.domain.PriorRelation;
import com.pitchmap.basecamp.infra.BasecampSearchCondition;
import com.pitchmap.basecamp.infra.BasecampSearchMapper;
import com.pitchmap.basecamp.infra.BasecampSearchRow;
import com.pitchmap.basecamp.infra.ConfirmedScheduleRow;
import com.pitchmap.basecamp.infra.ViewerHistoryRow;
import com.pitchmap.spot.application.SpotSearchBox;
import com.pitchmap.trust.application.TrustDetail;
import com.pitchmap.trust.application.TrustSummaryService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 영역이나 반경 안의 모집 중 베이스캠프를 출발일이 빠른 순서로 찾는다. 로그인한 요청자에게는 항목마다 신청할 수 있는지와 그 이유를 함께 준다.
 *
 * <p>신청 가능 여부는 한 페이지의 베이스캠프 전체에 대해 요청자의 신뢰 정보를 한 번, 이전 관계와 확정된 일정을 각각 한 번씩만 읽어서 판정한다.
 * 베이스캠프마다 조회하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BasecampSearchService {

    private static final double METERS_PER_KILOMETER = 1000;

    private final BasecampSearchMapper basecampSearchMapper;
    private final TrustSummaryService trustSummaryService;

    /**
     * 호출하면 query 조건에 맞는 모집 중 베이스캠프를 한 페이지 돌려준다. viewerId가 null이면 비로그인 요청자라서 신청 가능 여부를 채우지 않는다.
     *
     * <p>다음 페이지가 있는지 알려고 한 건을 더 읽고, 그 건은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public BasecampSearchPage search(Long viewerId, BasecampSearchQuery query) {
        long offset = (long) query.page() * query.size();
        List<BasecampSearchRow> rows =
                basecampSearchMapper.selectRecruiting(toCondition(query), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<BasecampSearchRow> pageRows = rows.stream().limit(query.size()).toList();
        EligibilityJudge judge = viewerId == null ? null : judgeFor(viewerId, pageRows);
        List<BasecampSearchItem> content =
                pageRows.stream().map(row -> toItem(row, judge)).toList();
        return new BasecampSearchPage(content, query.page(), query.size(), hasNext);
    }

    private static BasecampSearchCondition toCondition(BasecampSearchQuery query) {
        return switch (query.region()) {
            case BasecampSearchQuery.Area area ->
                new BasecampSearchCondition(
                        new BasecampSearchCondition.Area(area.swLat(), area.swLng(), area.neLat(), area.neLng()),
                        null,
                        query.fromDate(),
                        query.toDate(),
                        query.hasVacancy());
            case BasecampSearchQuery.Radius radius ->
                new BasecampSearchCondition(
                        null, toNearby(radius), query.fromDate(), query.toDate(), query.hasVacancy());
        };
    }

    private static BasecampSearchCondition.Nearby toNearby(BasecampSearchQuery.Radius radius) {
        SpotSearchBox box = SpotSearchBox.around(radius.lat(), radius.lng(), radius.radiusKm());
        return new BasecampSearchCondition.Nearby(
                radius.lat(),
                radius.lng(),
                radius.radiusKm() * METERS_PER_KILOMETER,
                box.swLat(),
                box.swLng(),
                box.neLat(),
                box.neLng());
    }

    private EligibilityJudge judgeFor(long viewerId, List<BasecampSearchRow> pageRows) {
        if (pageRows.isEmpty()) {
            return null;
        }
        Applicant applicant = toApplicant(trustSummaryService.detail(viewerId));
        List<Long> basecampIds =
                pageRows.stream().map(BasecampSearchRow::basecampId).toList();
        Map<Long, PriorRelation> priorRelations = new HashMap<>();
        for (ViewerHistoryRow history : basecampSearchMapper.selectViewerHistory(viewerId, basecampIds)) {
            priorRelations.put(
                    history.basecampId(), PriorRelation.of(history.memberStatus(), history.applicationStatus()));
        }
        return new EligibilityJudge(applicant, priorRelations, basecampSearchMapper.selectConfirmedSchedules(viewerId));
    }

    // 신뢰 모듈의 연령대는 이름 문자열로 오고, 그 모듈의 enum을 참조할 수 없다. 그래서 합류 조건이 쓰는 십 단위 숫자로 여기서 바꾼다.
    private static Applicant toApplicant(TrustDetail trust) {
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

    private static BasecampSearchItem toItem(BasecampSearchRow row, EligibilityJudge judge) {
        JoinConditionSummary condition = new JoinConditionSummary(
                row.minTrustLevel(), row.ageGroupMin(), row.ageGroupMax(), row.sameGenderOnly());
        return new BasecampSearchItem(
                row.basecampId(),
                row.title(),
                new BasecampSearchItem.SpotSummary(
                        row.spotId(), row.spotName(), row.spotType(), row.spotLat(), row.spotLng()),
                row.startDate(),
                row.endDate(),
                row.capacity(),
                row.headcount(),
                row.status().name(),
                condition,
                judge == null ? null : judge.judge(row));
    }

    // 한 페이지에서 요청자 한 명의 신청 자격을 베이스캠프마다 판정하는 데 필요한 값을 모아 둔 것이다.
    private record EligibilityJudge(
            Applicant applicant, Map<Long, PriorRelation> priorRelations, List<ConfirmedScheduleRow> schedules) {

        JoinEligibility judge(BasecampSearchRow row) {
            JoinCondition condition = toJoinCondition(row);
            PriorRelation prior = priorRelations.getOrDefault(row.basecampId(), PriorRelation.NONE);
            return JoinEligibility.of(
                    JoinEligibilityPolicy.unmetReasons(condition, applicant, hasDateConflict(row), prior));
        }

        // 확정된 일정과 하루라도 겹치면 충돌이다. 자기 자신은 확정된 베이스캠프가 아니므로 모집 중인 검색 결과와 겹치지 않지만, 신청 검사가 같은 규칙을 쓸 수 있게 제외한다.
        private boolean hasDateConflict(BasecampSearchRow row) {
            return schedules.stream()
                    .filter(schedule -> schedule.basecampId() != row.basecampId())
                    .anyMatch(schedule -> JoinEligibilityPolicy.overlaps(
                            row.startDate(), row.endDate(), schedule.startDate(), schedule.endDate()));
        }

        private static JoinCondition toJoinCondition(BasecampSearchRow row) {
            JoinCondition condition = JoinCondition.none();
            if (row.minTrustLevel() != null) {
                condition = condition.withMinTrustLevel(row.minTrustLevel());
            }
            if (row.ageGroupMin() != null && row.ageGroupMax() != null) {
                condition = condition.withAgeGroupRange(row.ageGroupMin(), row.ageGroupMax());
            }
            if (row.sameGenderOnly()) {
                condition = condition.withSameGenderOnly(row.requiredGender());
            }
            return condition;
        }
    }
}
