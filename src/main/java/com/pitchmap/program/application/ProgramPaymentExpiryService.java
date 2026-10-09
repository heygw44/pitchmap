package com.pitchmap.program.application;

import com.pitchmap.program.infra.ExpiryTarget;
import com.pitchmap.program.infra.ProgramPaymentExpiryMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 결제 기한이 지난 결제 대기 신청을 만료로 바꿔 자리를 돌려준다.
 *
 * <p>이 클래스는 트랜잭션을 열지 않는다. 대상을 나눠 읽은 뒤 신청마다 {@link ProgramPaymentExpiryApplier}를 불러
 * 신청 하나를 트랜잭션 하나로 처리한다. 한 신청이 실패해도 나머지는 계속 처리하고, 실패한 신청은 다음 실행에서 다시 집는다.
 * 서버 여러 대가 동시에 돌려도 신청마다 조건부 UPDATE가 한 번만 성공하므로 중복 처리가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramPaymentExpiryService {

    // 한 번에 읽는 신청의 수다. 밀린 신청이 많아도 목록이 너무 커지지 않게 나눈다.
    static final int BATCH_SIZE = 500;

    private final ProgramPaymentExpiryMapper expiryMapper;
    private final ProgramPaymentExpiryApplier applier;
    private final Clock clock;

    /** 호출하면 이 시각 기준으로 결제 기한이 지난 결제 대기 신청을 모두 만료로 바꾸고, 실제로 바꾼 신청 수를 돌려준다. */
    public Result run() {
        Instant now = clock.instant();
        int expired = 0;
        long afterId = 0;
        while (true) {
            List<ExpiryTarget> targets = expiryMapper.selectDueTargets(now, afterId, BATCH_SIZE);
            for (ExpiryTarget target : targets) {
                if (expireOne(target, now)) {
                    expired++;
                }
            }
            if (targets.size() < BATCH_SIZE) {
                break;
            }
            afterId = targets.get(targets.size() - 1).applicationId();
        }
        log.info("program payment expiry completed expired={}", expired);
        return new Result(expired);
    }

    // 한 신청의 실패가 다른 신청의 처리를 막지 않게 여기서 잡는다. 실패한 신청은 상태가 그대로라 다음 실행의 대상이 된다.
    private boolean expireOne(ExpiryTarget target, Instant now) {
        try {
            return applier.expire(target, now);
        } catch (RuntimeException e) {
            log.error("program payment expiry failed applicationId={}", target.applicationId(), e);
            return false;
        }
    }

    /** @param expired 이번 실행에서 만료로 바꾼 신청 수 */
    public record Result(int expired) {}
}
