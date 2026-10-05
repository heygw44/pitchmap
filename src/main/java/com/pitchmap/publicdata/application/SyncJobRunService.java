package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.domain.SyncJobRun;
import com.pitchmap.publicdata.domain.SyncJobRunRepository;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기화 작업의 실행 기록을 남긴다. 기록을 바꾸는 메서드마다 트랜잭션을 따로 연다.
 * 그래서 작업이 외부 API를 부르는 동안 DB 커넥션을 붙잡지 않고, 페이지마다 남긴 진행 위치는 작업이 나중에 실패해도 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncJobRunService {

    static final String STALE_RUN_MESSAGE = "실행 기록이 오래 갱신되지 않아, 다음 실행이 작업 도중 서버가 꺼진 것으로 보고 실패로 처리했습니다.";

    private static final String RUNNING_UNIQUE_CONSTRAINT = "uk_sync_job_run_running";
    private static final String KEY_MARKER = " for key '";
    private static final int MAX_START_ATTEMPTS = 3;

    private final SyncJobRunStartService syncJobRunStartService;
    private final SyncJobRunRepository syncJobRunRepository;
    private final Clock clock;

    /**
     * 호출하면 새 실행 기록을 RUNNING으로 만들고, 이번 실행이 어디부터 처리할지 돌려준다.
     *
     * <p>직전 실행이 실패했으면 그 실행의 진행 위치를 이어받고, 완료했거나 기록이 없으면 처음부터 처리한다.
     * 직전 실행이 아직 RUNNING이고 {@code staleAfter} 안에 갱신됐으면 다른 실행이 진행 중이므로 기록을 만들지 않고 빈 값을 돌려준다.
     * 그보다 오래 갱신되지 않았으면 서버가 작업 도중 꺼진 것으로 보고, 그 기록을 실패로 처리한 뒤 진행 위치를 이어받는다.
     *
     * <p>두 호출이 같은 종류를 동시에 시작하면 둘 다 RUNNING 기록이 없다고 읽을 수 있다. 이때 DB는 종류마다 RUNNING 기록을 하나만 받으므로
     * 늦게 넣은 쪽에 유니크 제약 위반 예외를 던진다. 그 예외가 나면 시작 트랜잭션은 이미 롤백됐으므로, 이 메서드는 트랜잭션 밖에서 예외를 받아
     * 다른 실행이 진행 중일 때와 같이 빈 값을 돌려준다.
     *
     * <p>같은 종류를 동시에 시작한 호출이 여럿이면 InnoDB가 같은 키를 넣으려는 트랜잭션 중 하나를 교착 상태 오류로 중단시키기도 한다.
     * 중단된 트랜잭션은 롤백됐으므로, 이 메서드는 시작을 최대 {@value #MAX_START_ATTEMPTS}번까지 다시 시도한다.
     * 다시 시도하면 먼저 끝낸 쪽이 커밋한 RUNNING 기록을 읽어 빈 값을 돌려주거나 새로 넣는다. 모든 시도가 실패하면 마지막 예외를 던진다.
     */
    public Optional<SyncJobStart> begin(SyncJobType jobType, Duration staleAfter) {
        for (int attempt = 1; ; attempt++) {
            try {
                return syncJobRunStartService.start(jobType, staleAfter);
            } catch (DataIntegrityViolationException e) {
                if (!isRunningUniqueViolation(e)) {
                    throw e;
                }
                log.info("sync job already running jobType={} reason=concurrent start", jobType);
                return Optional.empty();
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_START_ATTEMPTS) {
                    throw e;
                }
                log.warn("sync job start lost a lock conflict, retrying jobType={} attempt={}", jobType, attempt);
            }
        }
    }

    // MySQL 메시지는 "Duplicate entry '값' for key '테이블.제약'" 형태다. 값은 작업 종류 이름이라 제약 이름과 겹치지 않지만,
    // 다른 유니크 제약과 섞어 읽지 않도록 마지막 "for key" 뒤만 본다.
    private static boolean isRunningUniqueViolation(DataIntegrityViolationException e) {
        String message = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
        if (message == null) {
            return false;
        }
        int marker = message.lastIndexOf(KEY_MARKER);
        return marker >= 0 && message.substring(marker + KEY_MARKER.length()).contains(RUNNING_UNIQUE_CONSTRAINT);
    }

    @Transactional
    public void recordProgress(long runId, String cursor, int processedDelta, int skippedDelta) {
        getRun(runId).recordProgress(cursor, processedDelta, skippedDelta, clock.instant());
    }

    @Transactional
    public void complete(long runId) {
        getRun(runId).complete(clock.instant());
    }

    @Transactional
    public void fail(long runId, String message) {
        getRun(runId).fail(message, clock.instant());
    }

    private SyncJobRun getRun(long runId) {
        return syncJobRunRepository
                .findById(runId)
                .orElseThrow(() -> new IllegalStateException("실행 기록이 없습니다. runId=" + runId));
    }

    /**
     * 이번 실행의 시작 정보.
     *
     * @param runId 새로 만든 실행 기록의 ID
     * @param resumeCursor 실패한 직전 실행이 마지막으로 처리를 마친 위치. 처음부터 처리하면 null이다.
     */
    public record SyncJobStart(long runId, String resumeCursor) {}
}
