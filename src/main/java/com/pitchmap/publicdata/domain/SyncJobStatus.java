package com.pitchmap.publicdata.domain;

/** 실행 기록의 상태. RUNNING에서 COMPLETED나 FAILED로 한 번만 바뀐다. */
public enum SyncJobStatus {
    RUNNING,
    COMPLETED,
    FAILED
}
