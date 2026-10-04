package com.pitchmap.member.infra;

import com.pitchmap.member.domain.EmailVerification;
import com.pitchmap.member.domain.EmailVerificationRepository;
import com.pitchmap.member.domain.SendWindow;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 save를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface EmailVerificationJpaRepository
        extends JpaRepository<EmailVerification, Long>, EmailVerificationRepository {

    @Override
    EmailVerification save(EmailVerification emailVerification);

    // 같은 시각에 발급한 행이 둘일 수 있어서 시각이 아니라 id로 가장 나중 행을 고른다.
    Optional<EmailVerification> findFirstByMemberIdOrderByIdDesc(long memberId);

    @Override
    default Optional<EmailVerification> findLatestByMemberId(long memberId) {
        return findFirstByMemberIdOrderByIdDesc(memberId);
    }

    @Override
    @Query("SELECT MAX(e.createdAt) FROM EmailVerification e WHERE e.memberId = :memberId")
    Optional<Instant> findLastSentAt(@Param("memberId") long memberId);

    @Override
    @Query("""
            SELECT new com.pitchmap.member.domain.SendWindow(COUNT(e), MIN(e.createdAt))
            FROM EmailVerification e
            WHERE e.memberId = :memberId AND e.createdAt > :since
            """)
    SendWindow summarizeSentToMemberSince(@Param("memberId") long memberId, @Param("since") Instant since);

    @Override
    @Query("""
            SELECT new com.pitchmap.member.domain.SendWindow(COUNT(e), MIN(e.createdAt))
            FROM EmailVerification e
            WHERE e.requestIp = :requestIp AND e.createdAt > :since
            """)
    SendWindow summarizeSentByIpSince(@Param("requestIp") String requestIp, @Param("since") Instant since);
}
