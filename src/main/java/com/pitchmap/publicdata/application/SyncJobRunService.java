package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.domain.SyncJobRun;
import com.pitchmap.publicdata.domain.SyncJobRunRepository;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기화 작업의 실행 기록을 남긴다. 메서드마다 트랜잭션을 따로 연다.
 * 그래서 작업이 외부 API를 부르는 동안 DB 커넥션을 붙잡지 않고, 페이지마다 남긴 진행 위치는 작업이 나중에 실패해도 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncJobRunService {

    static final String STALE_RUN_MESSAGE = "실행 기록이 오래 갱신되지 않아, 다음 실행이 작업 도중 서버가 꺼진 것으로 보고 실패로 처리했습니다.";

    private final SyncJobRunRepository syncJobRunRepository;
    private final Clock clock;

    /**
     * 호출하면 새 실행 기록을 RUNNING으로 만들고, 이번 실행이 어디부터 처리할지 돌려준다.
     *
     * <p>직전 실행이 실패했으면 그 실행의 진행 위치를 이어받고, 완료했거나 기록이 없으면 처음부터 처리한다.
     * 직전 실행이 아직 RUNNING이고 {@code staleAfter} 안에 갱신됐으면 다른 실행이 진행 중이므로 기록을 만들지 않고 빈 값을 돌려준다.
     * 그보다 오래 갱신되지 않았으면 서버가 작업 도중 꺼진 것으로 보고, 그 기록을 실패로 처리한 뒤 진행 위치를 이어받는다.
     */
    @Transactional
    public Optional<SyncJobStart> begin(SyncJobType jobType, Duration staleAfter) {
        Instant now = clock.instant();
        Optional<SyncJobRun> latest = syncJobRunRepository.findFirstByJobTypeOrderByIdDesc(jobType);
        if (latest.isPresent() && latest.get().isRunning()) {
            SyncJobRun running = latest.get();
            if (!running.isStale(now, staleAfter)) {
                log.info("sync job already running jobType={} runId={}", jobType, running.getId());
                return Optional.empty();
            }
            running.fail(STALE_RUN_MESSAGE, now);
            log.warn("stale sync job marked failed jobType={} runId={}", jobType, running.getId());
        }
        String resumeCursor = latest.filter(SyncJobRun::isFailed)
                .map(SyncJobRun::getProgressCursor)
                .orElse(null);
        SyncJobRun started = syncJobRunRepository.save(SyncJobRun.start(jobType, resumeCursor, now));
        return Optional.of(new SyncJobStart(started.getId(), resumeCursor));
    }

    @Transactional
    public void recordProgress(long runId, String cursor, int processedDelta) {
        getRun(runId).recordProgress(cursor, processedDelta, clock.instant());
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
