package com.pitchmap.trust.infra;

import com.pitchmap.trust.domain.CompanionReview;
import com.pitchmap.trust.domain.CompanionReviewRepository;
import org.springframework.data.jpa.repository.JpaRepository;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface CompanionReviewJpaRepository extends JpaRepository<CompanionReview, Long>, CompanionReviewRepository {

    @Override
    CompanionReview saveAndFlush(CompanionReview review);
}
