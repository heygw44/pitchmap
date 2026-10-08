package com.pitchmap.common.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 만든 지 하루가 지난 멱등성 기록을 지운다. 이 클래스는 {@code common}에 있어 트랜잭션을 열지 않고,
 * 삭제 문장마다 자동 커밋한다. 그래서 쌓인 행이 많아도 한 번에 오래 잠그지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyRecordCleaner {

    static final Duration RETENTION = Duration.ofDays(1);

    // 한 번에 지우는 행 수의 상한이다. 한 번의 삭제가 너무 오래 행을 잠그지 않게 나눈다.
    static final int BATCH_SIZE = 1000;

    private final IdempotencyRecordMapper recordMapper;
    private final Clock clock;

    /** 호출하면 BATCH_SIZE개씩 지우기를 대상이 없어질 때까지 되풀이하고, 지운 행 수를 돌려준다. */
    public int deleteExpired() {
        Instant cutoff = Instant.now(clock).minus(RETENTION);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = recordMapper.deleteOlderThan(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("idempotency record deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
