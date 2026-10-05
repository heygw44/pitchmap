package com.pitchmap.member.application;

import com.pitchmap.member.domain.PasswordResetRequestPolicy;
import com.pitchmap.member.infra.PasswordResetThrottleMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마지막 요청이 24시간보다 오래된 비밀번호 재설정 요청 횟수 행을 지운다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetThrottleCleanupService {

    // 한 번에 지우는 행 수의 상한이다. 쌓인 행이 많을 때 한 번의 삭제가 너무 오래 행을 잠그지 않게 나눈다.
    static final int BATCH_SIZE = 1000;

    private final PasswordResetThrottleMapper throttleMapper;
    private final Clock clock;

    /** 호출하면 삭제 대상 행을 BATCH_SIZE개씩 지우기를 대상이 없어질 때까지 되풀이하고, 지운 행 수를 돌려준다. */
    @Transactional
    public int deleteStaleThrottles() {
        Instant cutoff = Instant.now(clock).minus(PasswordResetRequestPolicy.STALE_AFTER);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = throttleMapper.deleteStale(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("password reset throttle deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
