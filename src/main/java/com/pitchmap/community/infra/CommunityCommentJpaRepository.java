package com.pitchmap.community.infra;

import com.pitchmap.community.domain.CommunityComment;
import com.pitchmap.community.domain.CommunityCommentRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface CommunityCommentJpaRepository
        extends JpaRepository<CommunityComment, Long>, CommunityCommentRepository {

    @Override
    CommunityComment saveAndFlush(CommunityComment comment);

    @Override
    Optional<CommunityComment> findById(Long id);

    @Override
    void flush();
}
