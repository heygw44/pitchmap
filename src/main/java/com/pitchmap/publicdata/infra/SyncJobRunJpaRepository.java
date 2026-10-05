package com.pitchmap.publicdata.infra;

import com.pitchmap.publicdata.domain.SyncJobRun;
import com.pitchmap.publicdata.domain.SyncJobRunRepository;
import com.pitchmap.publicdata.domain.SyncJobType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface SyncJobRunJpaRepository extends JpaRepository<SyncJobRun, Long>, SyncJobRunRepository {

    @Override
    SyncJobRun save(SyncJobRun run);

    @Override
    Optional<SyncJobRun> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SyncJobRun> findFirstByJobTypeOrderByIdDesc(SyncJobType jobType);
}
