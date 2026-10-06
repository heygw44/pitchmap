package com.pitchmap.publicdata.api;

import com.pitchmap.publicdata.application.SyncJobRunView;
import com.pitchmap.publicdata.domain.SyncJobStatus;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Instant;

public record SyncJobRunResponse(
        long jobRunId,
        SyncJobType jobType,
        SyncJobStatus status,
        int processedCount,
        int skippedCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {

    static SyncJobRunResponse from(SyncJobRunView view) {
        return new SyncJobRunResponse(
                view.jobRunId(),
                view.jobType(),
                view.status(),
                view.processedCount(),
                view.skippedCount(),
                view.errorMessage(),
                view.startedAt(),
                view.finishedAt());
    }
}
