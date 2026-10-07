package com.pitchmap.basecamp.infra;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampRepository;
import com.pitchmap.basecamp.domain.BasecampStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface BasecampJpaRepository extends JpaRepository<Basecamp, Long>, BasecampRepository {

    @Override
    Basecamp saveAndFlush(Basecamp basecamp);

    @Override
    Optional<Basecamp> findById(Long id);

    @Override
    long countByLeaderIdAndStatusIn(long leaderId, Collection<BasecampStatus> statuses);
}
