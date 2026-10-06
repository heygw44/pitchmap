package com.pitchmap.publicdata.application;

import com.pitchmap.publicdata.domain.SyncJobStatus;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Instant;

/** 실행 기록 하나의 조회 결과. 실행 중이면 {@code finishedAt}이 null이고, 실패했을 때만 {@code errorMessage}가 있다. */
public record SyncJobRunView(
        long jobRunId,
        SyncJobType jobType,
        SyncJobStatus status,
        int processedCount,
        int skippedCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {}
