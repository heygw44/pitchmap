package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.application.SyncJobRunService.SyncJobStart;
import com.pitchmap.publicdata.domain.SyncJobType;
import com.pitchmap.spot.application.ParkAreaJudgeService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공원 경계가 바뀐 뒤 모든 박지의 공원 경고를 새 경계로 다시 판정한다.
 *
 * <p>판정은 장소 모듈이 박지 전체를 UPDATE 한 번으로 바꾸고 트랜잭션 하나로 커밋한다. 그래서 도중에 실패하면 DB가 모두 되돌리고, 다음 실행이 이어서
 * 처리할 위치도 없다. 따라서 서비스는 실행 기록에 진행 위치를 쓰지 않고, 직전 실행이 실패했어도 처음부터 다시 판정한다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 실행 기록과 재판정은 각 서비스의 트랜잭션에서 따로 커밋한다. 그래서 재판정이 실패해도 실패한 실행 기록은 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BakjiRejudgeService {

    private final SyncJobRunService syncJobRunService;
    private final ParkAreaJudgeService parkAreaJudgeService;
    private final BakjiRejudgeProperties properties;

    /**
     * 호출하면 실행 기록을 시작하고, 박지를 한 번 재판정한 결과를 돌려준다. 다른 재판정이 진행 중이라 시작하지 않았으면 빈 값을 돌려준다. 실패하면 실행
     * 기록을 FAILED로 남기고 받은 예외를 그대로 다시 던진다.
     */
    public Optional<BakjiRejudgeResult> rejudge() {
        return syncJobRunService
                .begin(SyncJobType.BAKJI_REJUDGE, properties.staleAfter())
                .map(this::run);
    }

    /**
     * 호출하면 이미 시작한 실행 기록 start로 박지를 재판정하고, 성공하면 실행 기록을 COMPLETED로 바꾼다. 실행 기록을 새로 시작하지 않으므로, 호출하는 쪽이
     * {@link SyncJobRunService#begin}으로 BAKJI_REJUDGE 실행을 먼저 시작해 둬야 한다. 실패하면 실행 기록을 FAILED로 남기고 받은 예외를 그대로 다시
     * 던진다.
     */
    public BakjiRejudgeResult run(SyncJobStart start) {
        long runId = start.runId();
        try {
            log.info("bakji rejudge started runId={}", runId);
            BakjiRejudgeResult result = new BakjiRejudgeResult(parkAreaJudgeService.rejudgeBakjis());
            syncJobRunService.recordProgress(runId, null, result.judgedCount(), 0);
            syncJobRunService.complete(runId);
            log.info("bakji rejudge completed runId={} judged={}", runId, result.judgedCount());
            return result;
        } catch (RuntimeException e) {
            recordFailure(runId, e);
            throw e;
        }
    }

    // 예외 메시지에는 비밀값과 개인정보가 없다. 그래서 메시지를 그대로 실행 기록에 남긴다.
    // 실패 기록마저 실패하면 원래 예외가 더 중요하므로, 기록 실패는 원래 예외에 덧붙여 함께 던진다.
    private void recordFailure(long runId, RuntimeException cause) {
        log.warn(
                "bakji rejudge failed runId={} error={}",
                runId,
                cause.getClass().getSimpleName());
        try {
            syncJobRunService.fail(runId, cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }
}
