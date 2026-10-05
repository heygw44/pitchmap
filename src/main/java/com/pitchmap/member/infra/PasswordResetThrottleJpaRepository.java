package com.pitchmap.member.infra;

import com.pitchmap.member.domain.PasswordResetThrottle;
import com.pitchmap.member.domain.PasswordResetThrottleRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetThrottleJpaRepository
        extends JpaRepository<PasswordResetThrottle, String>, PasswordResetThrottleRepository {

    // 이름만으로는 Spring Data가 잠금 쿼리를 만들 수 없어서 JPQL을 직접 쓴다.
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetThrottle t WHERE t.key = :key")
    Optional<PasswordResetThrottle> findByKeyForUpdate(@Param("key") String key);
}
