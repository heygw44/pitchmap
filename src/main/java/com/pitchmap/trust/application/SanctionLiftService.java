package com.pitchmap.trust.application;

import com.pitchmap.common.audit.AdminAuditAction;
import com.pitchmap.common.audit.AdminAuditRecorder;
import com.pitchmap.common.audit.AdminAuditTargetType;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.member.application.MemberSuspensionService;
import com.pitchmap.trust.domain.Sanction;
import com.pitchmap.trust.domain.SanctionRepository;
import com.pitchmap.trust.domain.SanctionStatus;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자가 적용 중인 제재를 해제한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionLiftService {

    private final SanctionRepository sanctionRepository;
    private final MemberSuspensionService memberSuspensionService;
    private final SuspensionResyncer suspensionResyncer;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    /**
     * 호출하면 적용 중인 제재를 해제하고, 정지였으면 회원의 정지 상태를 남은 정지에 맞춰 다시 정한다. 경고와 확정 정지 모두 해제할 수 있다.
     * 해제한 제재는 다음 제재 단계를 계산할 때 빠진다. 이미 정리한 베이스캠프와 결제 대기 신청은 되돌리지 않는다.
     *
     * <p>회원 행, 제재 행 순서로 잠근다. 제재 행을 먼저 읽으면 같은 제재를 동시에 해제하는 요청이 오래된 상태를 보고 둘 다 통과할 수 있어서,
     * 잠금 전에는 회원 ID만 읽고 엔티티는 잠금을 쥔 뒤에 읽는다. 제재가 없으면 NOT_FOUND, 적용 중이 아니면 SANCTION_INVALID_STATE로 거부한다.
     */
    @Transactional
    public SanctionLiftResult lift(long sanctionId, long adminId) {
        long memberId = sanctionRepository
                .findMemberIdById(sanctionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        memberSuspensionService.lockForSanction(memberId);
        Sanction sanction = sanctionRepository
                .findByIdForUpdate(sanctionId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        SanctionStatus before = sanction.getStatus();
        sanction.lift(adminId, clock.instant());
        if (sanction.getType().isSuspension()) {
            suspensionResyncer.resync(memberId);
        }
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.SANCTION_LIFT,
                AdminAuditTargetType.SANCTION,
                sanctionId,
                new TrustAuditDetails.Lift(
                        memberId,
                        sanction.getType().name(),
                        sanction.getLevel() == null ? null : sanction.getLevel().intValue(),
                        before.name(),
                        sanction.getStatus().name()));
        log.info("sanction lifted sanctionId={} memberId={} adminId={}", sanctionId, memberId, adminId);
        return new SanctionLiftResult(sanctionId, sanction.getStatus().name());
    }
}
