package com.pitchmap.member.application;

import com.pitchmap.member.infra.LoginHistoryCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 만든 지 3개월이 지난 로그인 기록을 지운다. 이 서비스는 트랜잭션을 열지 않고 삭제 문장마다 자동 커밋한다.
 * 기록이 많이 쌓였을 때 전체를 한 트랜잭션으로 묶으면 오래 걸리는 동안 행 잠금을 쥐고 있기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryCleanupService {

    /** 로그인 기록을 보관하는 기간이다. 달력 기준 3개월이라 일수가 아니라 Period로 둔다. */
    static final Period RETENTION = Period.ofMonths(3);

    // 한 번에 지우는 행 수의 상한이다. 한 번의 삭제가 너무 오래 행을 잠그지 않게 나눈다.
    static final int BATCH_SIZE = 1000;

    private final LoginHistoryCleanupMapper cleanupMapper;
    private final Clock clock;

    /**
     * 호출하면 만든 시각이 지금(UTC)에서 3개월을 뺀 시각보다 먼저인 기록을 BATCH_SIZE개씩 지우기를 대상이 없어질 때까지
     * 되풀이하고, 지운 행 수를 돌려준다. 정확히 3개월 된 기록은 남긴다.
     */
    public int deleteExpired() {
        Instant cutoff =
                Instant.now(clock).atZone(ZoneOffset.UTC).minus(RETENTION).toInstant();
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = cleanupMapper.deleteOlderThan(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("login history deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
