package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.application.SyncJobRunService.SyncJobStart;
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
 * 실행 기록을 시작하는 트랜잭션만 맡는다. {@link SyncJobRunService#begin}만 이 클래스를 부른다.
 *
 * <p>같은 종류를 동시에 시작하면 DB가 이 트랜잭션 안의 INSERT에서 유니크 제약 위반 예외를 던진다. 예외가 트랜잭션 안에서 나면 Spring이 그 트랜잭션을
 * 롤백 전용으로 표시하므로, 같은 트랜잭션 안에서 예외를 잡아 빈 값을 돌려주면 커밋할 때 다시 예외가 난다. 그래서 트랜잭션을 이 클래스에 따로 두고,
 * {@code begin}이 트랜잭션 밖에서 예외를 받는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class SyncJobRunStartService {

    private final SyncJobRunRepository syncJobRunRepository;
    private final Clock clock;

    @Transactional
    public Optional<SyncJobStart> start(SyncJobType jobType, Duration staleAfter) {
        Instant now = clock.instant();
        Optional<SyncJobRun> latest = syncJobRunRepository.findFirstByJobTypeOrderByIdDesc(jobType);
        if (latest.isPresent() && latest.get().isRunning()) {
            SyncJobRun running = latest.get();
            if (!running.isStale(now, staleAfter)) {
                log.info("sync job already running jobType={} runId={}", jobType, running.getId());
                return Optional.empty();
            }
            failStaleRun(running, now);
        }
        String resumeCursor = latest.filter(SyncJobRun::isFailed)
                .map(SyncJobRun::getProgressCursor)
                .orElse(null);
        SyncJobRun started = syncJobRunRepository.save(SyncJobRun.start(jobType, resumeCursor, now));
        return Optional.of(new SyncJobStart(started.getId(), resumeCursor));
    }

    // DB는 종류마다 RUNNING 기록을 하나만 받는다. 그런데 Hibernate는 새 기록의 INSERT를 바로 보내고 기존 기록의 UPDATE는 커밋할 때 보낸다.
    // 그러면 오래된 기록이 아직 RUNNING인 채로 새 RUNNING 기록이 들어가 제약 위반이 난다. 그래서 오래된 기록을 실패로 바꾼 뒤 바로 DB에 쓴다.
    private void failStaleRun(SyncJobRun running, Instant now) {
        running.fail(SyncJobRunService.STALE_RUN_MESSAGE, now);
        syncJobRunRepository.flush();
        log.warn("stale sync job marked failed jobType={} runId={}", running.getJobType(), running.getId());
    }
}
