package com.pitchmap.publicdata.infra;

import com.pitchmap.publicdata.domain.SyncJobStatus;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Instant;

/** 실행 기록 목록의 한 행. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다. */
public record SyncJobRunRow(
        long id,
        SyncJobType jobType,
        SyncJobStatus status,
        int processedCount,
        int skippedCount,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {}
