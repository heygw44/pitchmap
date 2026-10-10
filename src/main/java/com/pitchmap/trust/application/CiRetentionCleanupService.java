package com.pitchmap.trust.application;

import com.pitchmap.trust.infra.CiRetentionCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 탈퇴한 회원의 CI 해시 보관 기한이 지난 본인확인 행을 지운다. 행이 지워지면 같은 사람이 새 계정으로 본인확인할 수 있다.
 * 이 서비스는 트랜잭션을 열지 않고 삭제 문장마다 자동 커밋해서, 한 번에 오래 행을 잠그지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CiRetentionCleanupService {

    // 한 번에 지우는 행 수의 상한이다. 한 번의 삭제가 너무 오래 행을 잠그지 않게 나눈다.
    static final int BATCH_SIZE = 1000;

    private final CiRetentionCleanupMapper cleanupMapper;
    private final Clock clock;

    /**
     * 호출하면 보관 기한이 지금과 같거나 지난 행을 BATCH_SIZE개씩 지우기를 대상이 없어질 때까지 되풀이하고, 지운 행 수를
     * 돌려준다. 보관 기한이 없는 행(탈퇴하지 않은 회원)은 지우지 않는다.
     */
    public int deleteRetentionExpired() {
        Instant now = Instant.now(clock);
        int deletedTotal = 0;
        int deleted;
        do {
            deleted = cleanupMapper.deleteRetentionExpired(now, BATCH_SIZE);
            deletedTotal += deleted;
        } while (deleted >= BATCH_SIZE);
        log.info("identity verification deleted count={}", deletedTotal);
        return deletedTotal;
    }
}
