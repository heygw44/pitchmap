package com.pitchmap.publicdata.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.publicdata.application.SyncJobRunService.SyncJobStart;
import com.pitchmap.publicdata.domain.PublicDataErrorCode;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Duration;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * 관리자가 요청한 적재·동기화 작업을 시작한다.
 *
 * <p>작업은 몇 분씩 걸릴 수 있어서 요청 스레드에서 끝까지 돌리지 않는다. 이 서비스는 실행 기록을 RUNNING으로 만들어 커밋한 뒤 작업을 전용 실행기에 넘기고,
 * 바로 실행 기록 ID를 돌려준다. 그래서 관리자는 이 ID로 실행 기록을 조회해 진행 상황과 결과를 본다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 실행 기록은 {@link SyncJobRunService}가 자기 트랜잭션에서 커밋하므로, 작업 스레드가 실행 기록을 읽을 때
 * 이미 DB에 있다.
 */
@Slf4j
@Service
public class SyncJobLauncher {

    private static final String REJECTED_MESSAGE = "작업 실행기에 빈 스레드가 없어 작업을 시작하지 못했습니다.";

    private final SyncJobRunService syncJobRunService;
    private final GoCampingSyncService goCampingSyncService;
    private final ForestLoadService forestLoadService;
    private final ParkBoundaryLoadService parkBoundaryLoadService;
    private final GoCampingSyncProperties goCampingSyncProperties;
    private final ForestLoadProperties forestLoadProperties;
    private final ParkBoundaryLoadProperties parkBoundaryLoadProperties;
    private final BakjiRejudgeService bakjiRejudgeService;
    private final BakjiRejudgeProperties bakjiRejudgeProperties;
    private final ThreadPoolTaskExecutor syncJobExecutor;

    SyncJobLauncher(
            SyncJobRunService syncJobRunService,
            GoCampingSyncService goCampingSyncService,
            ForestLoadService forestLoadService,
            ParkBoundaryLoadService parkBoundaryLoadService,
            GoCampingSyncProperties goCampingSyncProperties,
            ForestLoadProperties forestLoadProperties,
            ParkBoundaryLoadProperties parkBoundaryLoadProperties,
            BakjiRejudgeService bakjiRejudgeService,
            BakjiRejudgeProperties bakjiRejudgeProperties,
            @Qualifier(SyncJobExecutorConfig.SYNC_JOB_EXECUTOR) ThreadPoolTaskExecutor syncJobExecutor) {
        this.syncJobRunService = syncJobRunService;
        this.goCampingSyncService = goCampingSyncService;
        this.forestLoadService = forestLoadService;
        this.parkBoundaryLoadService = parkBoundaryLoadService;
        this.goCampingSyncProperties = goCampingSyncProperties;
        this.forestLoadProperties = forestLoadProperties;
        this.parkBoundaryLoadProperties = parkBoundaryLoadProperties;
        this.bakjiRejudgeService = bakjiRejudgeService;
        this.bakjiRejudgeProperties = bakjiRejudgeProperties;
        this.syncJobExecutor = syncJobExecutor;
    }

    /**
     * 호출하면 작업 종류의 실행 기록을 만들고 작업을 실행기에 넘긴 뒤, 작업이 끝나기를 기다리지 않고 실행 기록 ID를 돌려준다.
     *
     * <p>같은 종류가 이미 실행 중이면 {@code SYNC_JOB_ALREADY_RUNNING}을 던진다. 공원 경계 적재가 성공하면 같은 작업 스레드가 이어서 박지 재판정을
     * 시작하고, 그 재판정은 별도의 실행 기록으로 남는다.
     */
    public long launch(SyncJobType jobType) {
        Job job = jobFor(jobType);
        SyncJobStart start = syncJobRunService
                .begin(jobType, job.staleAfter())
                .orElseThrow(() -> new BusinessException(PublicDataErrorCode.SYNC_JOB_ALREADY_RUNNING));
        submit(jobType, start, job);
        log.info("sync job launched jobType={} runId={}", jobType, start.runId());
        return start.runId();
    }

    private Job jobFor(SyncJobType jobType) {
        return switch (jobType) {
            case GOCAMPING -> new Job(goCampingSyncProperties.staleAfter(), goCampingSyncService::run);
            case FOREST -> new Job(forestLoadProperties.staleAfter(), forestLoadService::run);
            case PARK_BOUNDARY -> new Job(parkBoundaryLoadProperties.staleAfter(), this::loadParkBoundaryThenRejudge);
            case BAKJI_REJUDGE -> new Job(bakjiRejudgeProperties.staleAfter(), bakjiRejudgeService::run);
        };
    }

    // 경계가 바뀌면 박지의 공원 경고도 새 경계로 맞춰야 하므로, 적재가 성공한 뒤에만 재판정을 시작한다. 적재가 예외를 던지면 재판정은 시작하지 않는다.
    // 다른 재판정이 이미 돌고 있으면 새로 시작하지 않고 WARN 로그만 남긴다.
    private void loadParkBoundaryThenRejudge(SyncJobStart start) {
        parkBoundaryLoadService.run(start);
        if (bakjiRejudgeService.rejudge().isEmpty()) {
            log.warn("bakji rejudge skipped because another rejudge is running parkBoundaryRunId={}", start.runId());
        }
    }

    // 실행기가 작업을 받지 않으면 작업은 시작조차 하지 않았으므로, 방금 만든 RUNNING 기록을 실패로 바꾼다.
    // 그러지 않으면 그 기록이 오래된 기록으로 처리될 때까지 같은 종류의 작업을 다시 시작할 수 없다.
    private void submit(SyncJobType jobType, SyncJobStart start, Job job) {
        try {
            syncJobExecutor.execute(() -> runJob(jobType, start, job));
        } catch (TaskRejectedException e) {
            log.warn("sync job rejected jobType={} runId={}", jobType, start.runId());
            syncJobRunService.fail(start.runId(), REJECTED_MESSAGE);
            throw e;
        }
    }

    // 작업 스레드는 호출 경로의 맨 끝이라 예외를 받아 줄 호출자가 없다. 작업 서비스는 실패를 이미 실행 기록에 FAILED로 남겼으므로,
    // 여기서는 어느 작업이 실패했는지 드러나는 ERROR 로그만 남긴다.
    private static void runJob(SyncJobType jobType, SyncJobStart start, Job job) {
        try {
            job.runner().accept(start);
        } catch (RuntimeException e) {
            log.error("sync job failed jobType={} runId={}", jobType, start.runId(), e);
        }
    }

    private record Job(Duration staleAfter, Consumer<SyncJobStart> runner) {}
}
