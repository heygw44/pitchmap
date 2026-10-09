package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.RemovalCause;
import com.pitchmap.basecamp.infra.BasecampSanctionCleanupMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 이용 정지 제재를 받았거나 탈퇴한 회원이 걸친 진행 중 베이스캠프(모집 중, 마감, 확정)를 모두 정리한다.
 *
 * <p>이 클래스는 트랜잭션을 열지 않는다. 대상 ID를 읽은 뒤 베이스캠프마다 {@link BasecampSanctionCleanupApplier}를 불러
 * 베이스캠프 하나를 트랜잭션 하나로 처리한다. 하나라도 실패하면 나머지를 끝까지 처리한 뒤 예외를 던져서, 이벤트를 다시 처리하게 한다.
 * 이미 정리한 베이스캠프는 다시 처리해도 아무것도 바뀌지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BasecampSanctionCleanupService {

    private final BasecampSanctionCleanupMapper cleanupMapper;
    private final BasecampSanctionCleanupApplier applier;

    /**
     * 호출하면 memberId인 회원이 ACTIVE 멤버이거나 대기 중인 신청자인 진행 중 베이스캠프를 하나씩 정리한다.
     * cause는 정리하는 이유로, 캠프 리더의 베이스캠프 취소 이유 등을 정한다. 한 베이스캠프가 실패해도 나머지는 계속 처리하고, 실패가 있었으면 마지막에 {@link IllegalStateException}을 던진다.
     */
    public void cleanUp(long memberId, RemovalCause cause) {
        List<Long> basecampIds = cleanupMapper.selectInProgressBasecampIds(memberId);
        List<Long> failedBasecampIds = new ArrayList<>();
        for (long basecampId : basecampIds) {
            try {
                applier.cleanUp(basecampId, memberId, cause);
            } catch (RuntimeException e) {
                log.error("basecamp cleanup failed cause={} memberId={} basecampId={}", cause, memberId, basecampId, e);
                failedBasecampIds.add(basecampId);
            }
        }
        log.info(
                "basecamp cleanup cause={} memberId={} targets={} failed={}",
                cause,
                memberId,
                basecampIds.size(),
                failedBasecampIds.size());
        if (!failedBasecampIds.isEmpty()) {
            throw new IllegalStateException(
                    "베이스캠프 정리에 실패한 베이스캠프가 있습니다. cause=" + cause + " basecampIds=" + failedBasecampIds);
        }
    }
}
