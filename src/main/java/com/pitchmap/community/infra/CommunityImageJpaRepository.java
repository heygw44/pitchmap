package com.pitchmap.community.infra;

import com.pitchmap.community.domain.CommunityImage;
import com.pitchmap.community.domain.CommunityImageRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface CommunityImageJpaRepository extends JpaRepository<CommunityImage, Long>, CommunityImageRepository {

    @Override
    CommunityImage saveAndFlush(CommunityImage image);

    @Override
    List<CommunityImage> findAllById(Iterable<Long> ids);

    // 같은 이미지를 동시에 글에 붙이려는 요청이 한 줄로 서도록 행을 잠근다. 이름만으로는 쿼리를 만들 수 없어서 JPQL을 직접 쓴다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM CommunityImage i WHERE i.id IN :ids ORDER BY i.id")
    List<CommunityImage> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Override
    @Query("SELECT i.id FROM CommunityImage i WHERE i.postId = :postId ORDER BY i.displayOrder")
    List<Long> findIdsByPostId(@Param("postId") long postId);
}
