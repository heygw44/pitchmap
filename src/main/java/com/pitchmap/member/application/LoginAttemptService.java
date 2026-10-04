package com.pitchmap.member.application;

import com.pitchmap.member.domain.LoginHistory;
import com.pitchmap.member.domain.LoginHistoryRepository;
import com.pitchmap.member.domain.LoginLockout;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시도 기록을 짧은 트랜잭션으로 읽고 쓴다.
 *
 * <p>{@link MemberLoginService#login}은 실패하면 예외를 던진다. 그 메서드 전체가 하나의 트랜잭션이면 예외 때문에 롤백되어
 * 실패 기록도 사라지고, 그러면 실패 횟수가 쌓이지 않아 잠금이 걸리지 않는다. 그래서 기록 쓰기는 이 별도 빈의 메서드마다
 * 따로 커밋한다. 비밀번호 해시 비교(BCrypt)도 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 한다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final Clock clock;

    /** 호출하면 그 이메일이 잠겨 있을 때 잠금이 풀릴 때까지 남은 시간을 돌려준다. 잠겨 있지 않으면 빈 값이다. */
    @Transactional(readOnly = true)
    public Optional<Duration> findRetryAfter(String attemptedEmail) {
        Instant now = Instant.now(clock);
        List<Instant> failureTimes =
                loginHistoryRepository.findFailureTimesSinceLastSuccess(attemptedEmail, LoginLockout.windowStart(now));
        return LoginLockout.lockedUntil(failureTimes, now).map(lockedUntil -> Duration.between(now, lockedUntil));
    }

    /** @param memberId 이메일에 해당하는 회원이 없으면 null이다. */
    @Transactional
    public void recordFailure(Long memberId, String attemptedEmail, String ip) {
        loginHistoryRepository.save(LoginHistory.failure(memberId, attemptedEmail, ip, Instant.now(clock)));
    }

    @Transactional
    public void recordSuccess(long memberId, String attemptedEmail, String ip) {
        loginHistoryRepository.save(LoginHistory.success(memberId, attemptedEmail, ip, Instant.now(clock)));
    }
}
