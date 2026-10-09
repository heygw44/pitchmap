package com.pitchmap.notification.application;

import com.pitchmap.notification.infra.OutboxEventCleanupMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 발행한 지 7일이 지난 아웃박스 이벤트를 지운다. 이 서비스는 트랜잭션을 열지 않고 삭제 문장마다 자동 커밋해서, 한 번에 오래 행을
 * 잠그지 않는다. 발행 전(PENDING)이거나 발행에 실패한(FAILED) 이벤트는 오래돼도 지우지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventCleaner {

    static final Duration RETENTION = Duration.ofDays(7);

    // 한 번에 지우는 행 수의 상한이다. 한 번의 삭제가 너무 오래 행을 잠그지 않게 나눈다.
    static final int BATCH_SIZE = 1000;

    private final OutboxEventCleanupMapper cleanupMapper;
    private final Clock clock;

    /** 호출하면 발행 시각이 7일 전보다 먼저인 발행 완료 이벤트를 BATCH_SIZE개씩 지우기를 대상이 없어질 때까지 되풀이하고, 지운 행 수를 돌려준다. */
    public int deletePublished() {
        Instant cutoff = Instant.now(clock).minus(RETENTION);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = cleanupMapper.deletePublishedBefore(cutoff, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("outbox event deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
