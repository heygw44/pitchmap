package com.pitchmap.program.infra;

import com.pitchmap.program.domain.Program;
import com.pitchmap.program.domain.ProgramRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface ProgramJpaRepository extends JpaRepository<Program, Long>, ProgramRepository {

    @Override
    Program saveAndFlush(Program program);

    @Override
    Optional<Program> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Program p where p.id = :id")
    Optional<Program> findByIdForUpdate(@Param("id") Long id);
}
