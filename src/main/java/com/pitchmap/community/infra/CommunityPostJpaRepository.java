package com.pitchmap.community.infra;

import com.pitchmap.community.domain.CommunityPost;
import com.pitchmap.community.domain.CommunityPostRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface CommunityPostJpaRepository extends JpaRepository<CommunityPost, Long>, CommunityPostRepository {

    @Override
    CommunityPost saveAndFlush(CommunityPost post);

    @Override
    Optional<CommunityPost> findById(Long id);

    // 이름만으로는 Spring Data가 쿼리를 만들 수 없어서 JPQL을 직접 쓴다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CommunityPost p WHERE p.id = :id")
    Optional<CommunityPost> findByIdForUpdate(@Param("id") long id);

    @Override
    void flush();
}
