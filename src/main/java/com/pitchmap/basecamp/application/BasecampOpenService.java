package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampDetails;
import com.pitchmap.basecamp.domain.BasecampErrorCode;
import com.pitchmap.basecamp.domain.BasecampException;
import com.pitchmap.basecamp.domain.BasecampOpenPolicy;
import com.pitchmap.basecamp.domain.BasecampRepository;
import com.pitchmap.basecamp.domain.Capacity;
import com.pitchmap.basecamp.domain.JoinCondition;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.spot.application.ActiveSpotChecker;
import com.pitchmap.trust.application.SanctionQueryService;
import com.pitchmap.trust.application.TrustDetail;
import com.pitchmap.trust.application.TrustSummaryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 본인확인을 마친 회원이 장소에 베이스캠프를 열고, 캠프 리더를 첫 멤버로 저장한다.
 *
 * <p>캠프 리더의 자격은 신뢰 단계가 1 이상이고 지금 적용 중인 이용 정지가 없는 것이다. 경고와 기간이 끝난 정지는 열기를 막지 않는다.
 * 정지를 확정하면 그 회원의 세션을 지우지만, 지우기 전에 이미 들어온 요청을 막으려고 여기서도 한 번 더 확인한다.
 *
 * <p>한 회원이 열어 둔 베이스캠프 수는 저장하기 전에 세어 확인할 뿐 잠그지 않는다. 그래서 같은 회원의 요청 두 개가 동시에 들어오면 둘 다
 * 개수 검사를 통과해 4개가 열릴 수 있다. 한 사람이 거의 동시에 연달아 여는 경우는 드물어서 지금은 막지 않고, 개선 후보로 따로 기록해 둔다.
 */
@Service
@RequiredArgsConstructor
public class BasecampOpenService {

    private static final int MIN_TRUST_LEVEL_TO_OPEN = 1;

    private final TrustSummaryService trustSummaryService;
    private final SanctionQueryService sanctionQueryService;
    private final ActiveSpotChecker activeSpotChecker;
    private final BasecampRepository basecampRepository;
    private final Clock clock;

    /**
     * 호출하면 leaderId인 회원이 command대로 베이스캠프를 열고 그 ID와 상태를 돌려준다.
     *
     * <p>거부하는 경우는 다음과 같다.
     * <ul>
     *   <li>출발일이 내일부터 60일 뒤 사이가 아니거나 박 수가 1~3박이 아니면 BASECAMP_SCHEDULE_INVALID
     *   <li>정원이 2~6명이 아니면 BASECAMP_CAPACITY_INVALID
     *   <li>신뢰 단계가 1 미만이면 TRUST_LEVEL_INSUFFICIENT
     *   <li>지금 적용 중인 이용 정지가 있으면 ACCESS_DENIED
     *   <li>합류 조건 값이 올바르지 않으면 INVALID_INPUT
     *   <li>장소가 없거나 ACTIVE가 아니면 NOT_FOUND
     *   <li>장소가 공원 경계 경고가 붙은 박지이면 BASECAMP_WARNING_SPOT
     *   <li>모집 중이거나 마감된 베이스캠프를 이미 3개 열었으면 BASECAMP_OPEN_LIMIT
     * </ul>
     */
    @Transactional
    public BasecampOpenResult open(long leaderId, BasecampOpenCommand command) {
        Instant now = clock.instant();
        BasecampOpenPolicy.requireSchedule(
                command.startDate(), command.endDate(), LocalDate.ofInstant(now, Basecamp.KOREA));
        Capacity capacity = Capacity.of(command.capacity());
        TrustDetail trust = trustSummaryService.detail(leaderId);
        requireTrustLevel(trust);
        requireNotSuspended(leaderId);
        JoinCondition joinCondition = JoinConditions.from(command.joinCondition(), trust);
        requireNotWarningSpot(command.spotId());
        BasecampOpenPolicy.requireUnderOpenLimit(
                basecampRepository.countByLeaderIdAndStatusIn(leaderId, BasecampOpenPolicy.OPEN_COUNTED_STATUSES));

        BasecampDetails details = new BasecampDetails(
                command.title(),
                command.description(),
                command.startDate(),
                command.endDate(),
                capacity,
                joinCondition);
        Basecamp basecamp = basecampRepository.saveAndFlush(open(leaderId, command.spotId(), details, now));
        return new BasecampOpenResult(basecamp.getId(), basecamp.getStatus().name());
    }

    private static void requireTrustLevel(TrustDetail trust) {
        if (trust.trustLevel() < MIN_TRUST_LEVEL_TO_OPEN) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
    }

    private void requireNotSuspended(long leaderId) {
        if (sanctionQueryService.hasActiveSuspension(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void requireNotWarningSpot(long spotId) {
        if (activeSpotChecker.isWarningBakji(spotId)) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_WARNING_SPOT);
        }
    }

    // 제목·설명 길이처럼 도메인이 IllegalArgumentException으로 알리는 요청 값 오류는 INVALID_INPUT으로 바꾼다.
    private static Basecamp open(long leaderId, long spotId, BasecampDetails details, Instant now) {
        try {
            return Basecamp.open(leaderId, spotId, details, now);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
    }
}
