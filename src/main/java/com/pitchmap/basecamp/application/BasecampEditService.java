package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampRepository;
import com.pitchmap.basecamp.domain.BasecampRevision;
import com.pitchmap.basecamp.domain.Capacity;
import com.pitchmap.basecamp.domain.JoinCondition;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.trust.application.TrustSummaryService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠프 리더가 베이스캠프의 연락 수단을 등록하거나 제목, 설명, 합류 조건, 정원을 고친다.
 *
 * <p>합류 승인·탈퇴·강퇴와 겹쳐도 정원이 어긋나지 않도록, 베이스캠프 행을 쓰기 잠금으로 읽어 같은 베이스캠프의 쓰기를 한 번에 하나씩 처리한다.
 */
@Service
@RequiredArgsConstructor
public class BasecampEditService {

    private final BasecampRepository basecampRepository;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    /**
     * 호출하면 leaderId인 캠프 리더가 연락 수단을 등록하거나 바꾸고 베이스캠프의 ID와 상태를 돌려준다.
     *
     * <p>다음 순서로 검사하고 처음 걸린 이유로 거부한다.
     * <ol>
     *   <li>베이스캠프가 없으면 NOT_FOUND
     *   <li>요청자가 캠프 리더가 아니면 ACCESS_DENIED
     *   <li>연락 수단이 비었거나 255자를 넘으면 INVALID_INPUT
     *   <li>완료되었거나 취소되었으면 BASECAMP_INVALID_STATE
     * </ol>
     */
    @Transactional
    public BasecampStatusResult registerContact(long basecampId, long leaderId, String contactInfo) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        try {
            basecamp.registerContact(contactInfo, clock.instant());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
        return resultOf(basecamp);
    }

    /**
     * 호출하면 leaderId인 캠프 리더가 command에 담긴 필드만 고치고 베이스캠프의 ID와 상태를 돌려준다. 보내지 않은 필드는 그대로 둔다.
     * 정원이 차서 자동 마감된 베이스캠프는 정원을 늘리면 다시 모집 중이 된다.
     *
     * <p>다음 순서로 검사하고 처음 걸린 이유로 거부한다.
     * <ol>
     *   <li>베이스캠프가 없으면 NOT_FOUND
     *   <li>요청자가 캠프 리더가 아니면 ACCESS_DENIED
     *   <li>정원이 2~6명이 아니면 BASECAMP_CAPACITY_INVALID
     *   <li>합류 조건 값이 올바르지 않거나 제목·설명이 비었거나 너무 길면 INVALID_INPUT
     *   <li>확정된 뒤이면 BASECAMP_INVALID_STATE
     *   <li>정원을 줄이면 BASECAMP_CAPACITY_INVALID
     * </ol>
     */
    @Transactional
    public BasecampStatusResult revise(long basecampId, long leaderId, BasecampReviseCommand command) {
        Basecamp basecamp = lockLedBy(basecampId, leaderId);
        BasecampRevision revision = toRevision(basecamp, command, leaderId);
        try {
            basecamp.revise(revision, clock.instant());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, e.getMessage());
        }
        return resultOf(basecamp);
    }

    // 현재 값에 요청에서 보낸 값만 덮어쓴다. 합류 조건을 보냈을 때만 캠프 리더의 본인확인 성별이 필요해서 그때만 신뢰 요약을 읽는다.
    private BasecampRevision toRevision(Basecamp basecamp, BasecampReviseCommand command, long leaderId) {
        Capacity capacity = command.capacity() == null ? basecamp.getCapacity() : Capacity.of(command.capacity());
        JoinCondition joinCondition = command.joinCondition() == null
                ? basecamp.getJoinCondition()
                : JoinConditions.from(command.joinCondition(), trustSummaryService.detail(leaderId));
        return new BasecampRevision(
                command.title() == null ? basecamp.getTitle() : command.title(),
                command.description() == null ? basecamp.getDescription() : command.description(),
                capacity,
                joinCondition);
    }

    // 베이스캠프 행의 잠금이 이 트랜잭션의 첫 조회여야 한다. MySQL의 반복 가능한 읽기에서는 잠그지 않는 조회가 먼저 일어나면
    // 그 시점에 읽기 스냅숏이 정해져서, 잠금을 얻은 뒤에도 먼저 끝난 다른 트랜잭션이 커밋한 변경이 보이지 않는다.
    private Basecamp lockLedBy(long basecampId, long leaderId) {
        Basecamp basecamp = basecampRepository
                .findByIdForUpdate(basecampId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!basecamp.isLeader(leaderId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return basecamp;
    }

    private static BasecampStatusResult resultOf(Basecamp basecamp) {
        return new BasecampStatusResult(basecamp.getId(), basecamp.getStatus().name());
    }
}
