package com.pitchmap.publicdata.domain;

import java.util.Optional;

public interface SyncJobRunRepository {

    SyncJobRun save(SyncJobRun run);

    Optional<SyncJobRun> findById(Long id);

    /**
     * 호출하면 그 작업 종류의 가장 최근 실행 기록을 쓰기 잠금으로 읽는다. 잠금은 호출한 트랜잭션이 끝날 때까지 유지된다.
     * 그래서 같은 작업을 동시에 시작하려는 호출은 한 줄로 서고, 뒤의 호출은 앞의 호출이 만든 실행 기록을 보게 된다.
     */
    Optional<SyncJobRun> findFirstByJobTypeOrderByIdDesc(SyncJobType jobType);
}
