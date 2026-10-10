package com.pitchmap.program.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.program.domain.Program;
import com.pitchmap.program.domain.ProgramApplicationStatus;
import com.pitchmap.program.domain.ProgramErrorCode;
import com.pitchmap.program.domain.ProgramException;
import com.pitchmap.program.domain.ProgramRepository;
import com.pitchmap.program.infra.ProgramApplicationMapper;
import com.pitchmap.program.infra.SeatClaim;
import com.pitchmap.trust.application.TrustSummaryService;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 행사에 선착순으로 신청한다. 정원이 N이면 결제 대기·확정 신청이 정확히 N건까지만 생긴다.
 *
 * <p>트랜잭션의 첫 쿼리로 행사 행을 쓰기 잠금으로 읽는다. 같은 행사에 들어온 신청은 이 잠금에서 줄을 서고,
 * 잠금을 얻은 뒤에 읽어야 먼저 커밋한 다른 신청이 보인다. 관리자의 행사 수정·취소도 같은 행을 잠그므로
 * 신청과 서로 줄을 선다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramApplyService {

    private final ProgramRepository programRepository;
    private final ProgramApplicationMapper programApplicationMapper;
    private final TrustSummaryService trustSummaryService;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 신청을 결제 대기 상태로 만들고 결제 기한을 돌려준다. 처음 걸린 오류 하나만 던진다.
     *
     * <ol>
     *   <li>행사가 없으면 NOT_FOUND
     *   <li>취소된 행사이면 PROGRAM_INVALID_STATE
     *   <li>신청 시작 전이거나 마감 뒤이면 PROGRAM_NOT_IN_APPLY_PERIOD
     *   <li>숙박 행사인데 신뢰 단계가 1 미만이면 TRUST_LEVEL_INSUFFICIENT
     *   <li>이미 결제 대기·확정 신청이 있으면 PROGRAM_ALREADY_APPLIED
     *   <li>남은 자리가 없으면 PROGRAM_SOLD_OUT
     * </ol>
     */
    @Transactional
    public ProgramApplyResult apply(long memberId, long programId) {
        Program program = programRepository
                .findByIdForUpdate(programId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Instant now = clock.instant();
        program.requireApplicableAt(now);
        requireTrustLevel(program, memberId);
        if (programApplicationMapper.existsActive(programId, memberId)) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_ALREADY_APPLIED);
        }
        SeatClaim claim = new SeatClaim(programId, memberId, program.paymentDueAt(now), now);
        claimSeat(claim);
        log.info("program applied programId={} applicationId={} memberId={}", programId, claim.getId(), memberId);
        return new ProgramApplyResult(
                claim.getId(),
                ProgramApplicationStatus.PENDING_PAYMENT.name(),
                claim.getPaymentDueAt(),
                program.getFee());
    }

    // 숙박 없는 행사는 신뢰 단계를 계산하지 않는다. 계산에는 DB 조회가 따르고, 이 시점에는 행사 행 잠금을 쥐고 있기 때문이다.
    private void requireTrustLevel(Program program, long memberId) {
        if (!program.isOvernight()) {
            return;
        }
        int trustLevel = trustSummaryService.summarize(memberId).trustLevel();
        if (!program.isTrustLevelSufficient(trustLevel)) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
    }

    private void claimSeat(SeatClaim claim) {
        int inserted;
        try {
            inserted = programApplicationMapper.insertIfSeatAvailable(claim);
        } catch (DuplicateKeyException e) {
            // 행사 행 잠금 때문에 보통은 일어나지 않는다. 그래도 DB의 유니크 제약이 던진 예외를 500으로 내보내지 않는다.
            throw new ProgramException(ProgramErrorCode.PROGRAM_ALREADY_APPLIED);
        }
        if (inserted == 0) {
            throw new ProgramException(ProgramErrorCode.PROGRAM_SOLD_OUT);
        }
    }
}
