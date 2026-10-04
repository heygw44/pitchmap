package com.pitchmap.member.infra;

import com.pitchmap.member.domain.LoginHistory;
import com.pitchmap.member.domain.LoginHistoryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 save를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface LoginHistoryJpaRepository extends JpaRepository<LoginHistory, Long>, LoginHistoryRepository {

    @Override
    LoginHistory save(LoginHistory loginHistory);

    // 마지막 성공을 시각이 아니라 id로 가른다. 시계가 같은 시각을 두 번 돌려줘도 성공 전후의 실패가 섞이지 않는다.
    @Override
    @Query("""
            SELECT h.createdAt FROM LoginHistory h
            WHERE h.attemptedEmail = :attemptedEmail
              AND h.success = false
              AND h.createdAt > :after
              AND h.id > COALESCE(
                  (SELECT MAX(s.id) FROM LoginHistory s
                   WHERE s.attemptedEmail = :attemptedEmail AND s.success = true), 0L)
            ORDER BY h.createdAt ASC
            """)
    List<Instant> findFailureTimesSinceLastSuccess(
            @Param("attemptedEmail") String attemptedEmail, @Param("after") Instant after);
}
