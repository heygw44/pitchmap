package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampRepository;
import com.pitchmap.basecamp.domain.JoinEligibilityPolicy;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.trust.application.TrustSummaryService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신청자가 대기 중인 자기 합류 신청을 취소한다. */
@Service
@RequiredArgsConstructor
public class BasecampApplicationCancelService {

    private final BasecampRepository basecampRepository;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원이 basecampId인 베이스캠프에 낸 대기 중인 신청을 취소한다. 모집 중이거나 마감된 때만 할 수 있다.
     *
     * <p>베이스캠프가 없거나 그 회원의 신청이 없으면 NOT_FOUND, 신뢰 단계가 1 미만이면 TRUST_LEVEL_INSUFFICIENT이다.
     * 모집 중이거나 마감된 상태가 아니거나 신청이 대기 중이 아니면 BASECAMP_INVALID_STATE이다.
     */
    @Transactional
    public void cancel(long basecampId, long memberId) {
        // 승인이나 거절과 겹쳐도 한 번에 하나만 처리하려고 베이스캠프 행을 잠근다. 이 잠금이 트랜잭션의 첫 조회여야 한다.
        // MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면 그 시점에 스냅숏이 정해져서, 잠금을 얻은 뒤에도
        // 다른 트랜잭션이 커밋한 변경이 보이지 않기 때문이다.
        Basecamp basecamp = basecampRepository
                .findByIdForUpdate(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (trustSummaryService.detail(memberId).trustLevel() < JoinEligibilityPolicy.BASE_TRUST_LEVEL) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
        basecamp.cancelApplication(memberId, clock.instant());
    }
}
