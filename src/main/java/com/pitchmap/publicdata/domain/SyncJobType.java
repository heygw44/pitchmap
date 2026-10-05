package com.pitchmap.publicdata.domain;

/** 공공데이터 적재·동기화 작업의 종류. sync_job_run.job_type에 이름 그대로 저장한다. */
public enum SyncJobType {
    GOCAMPING,
    FOREST,
    PARK_BOUNDARY,
    BAKJI_REJUDGE
}
