package com.pitchmap.program.application;

import com.pitchmap.program.domain.ProgramApplicationRepository;
import com.pitchmap.program.domain.ProgramCancelReason;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 이용 정지 제재를 받았거나 탈퇴한 회원의 행사 신청을 정리한다. 결제 대기 신청은 취소해서 자리를 돌려주고, 결제를 마친 신청은 관리자가 확인하도록 표시한다.
 *
 * <p>이 클래스는 트랜잭션을 열지 않는다. 대상 ID를 읽은 뒤 신청마다 {@link ProgramSanctionCleanupApplier}를 불러
 * 신청 하나를 트랜잭션 하나로 처리한다. 하나라도 실패하면 나머지를 끝까지 처리한 뒤 예외를 던져서, 이벤트를 다시 처리하게 한다.
 * 이미 정리한 신청은 다시 처리해도 아무것도 바뀌지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramSanctionCleanupService {

    private final ProgramApplicationRepository applicationRepository;
    private final ProgramSanctionCleanupApplier applier;

    /**
     * 호출하면 memberId인 회원의 결제 대기 신청을 reason(SANCTIONED 또는 WITHDRAWN)으로 하나씩 취소하고, 결제를 마친 신청에는 관리자 확인 요청 시각을 적는다.
     * 한 신청이 실패해도 나머지는 계속 처리하고, 실패가 있었으면 마지막에 {@link IllegalStateException}을 던진다.
     */
    public void cleanUp(long memberId, ProgramCancelReason reason) {
        List<Long> applicationIds = applicationRepository.findPendingPaymentIdsByMember(memberId);
        List<Long> failedApplicationIds = new ArrayList<>();
        int canceledCount = 0;
        for (long applicationId : applicationIds) {
            try {
                if (applier.cancelPending(applicationId, memberId, reason)) {
                    canceledCount++;
                }
            } catch (RuntimeException e) {
                log.error(
                        "program cleanup failed reason={} memberId={} applicationId={}",
                        reason,
                        memberId,
                        applicationId,
                        e);
                failedApplicationIds.add(applicationId);
            }
        }
        int reviewRequestedCount = 0;
        boolean reviewFailed = false;
        try {
            reviewRequestedCount = applier.requestReview(memberId);
        } catch (RuntimeException e) {
            log.error("program review request failed reason={} memberId={}", reason, memberId, e);
            reviewFailed = true;
        }
        log.info(
                "program cleanup reason={} memberId={} targets={} canceled={} reviewRequested={} failed={}",
                reason,
                memberId,
                applicationIds.size(),
                canceledCount,
                reviewRequestedCount,
                failedApplicationIds.size());
        if (!failedApplicationIds.isEmpty() || reviewFailed) {
            throw new IllegalStateException("행사 신청 정리에 실패한 항목이 있습니다. reason=" + reason + " applicationIds="
                    + failedApplicationIds + " reviewFailed=" + reviewFailed);
        }
    }
}
