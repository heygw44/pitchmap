package com.pitchmap.trust.application;

import com.pitchmap.trust.infra.ReportContentCleanupMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 처리가 끝난 지 1년이 지난 신고와 제재의 내용 컬럼(신고 내용, 처리 메모, 제재 사유)을 지운다. 신고와 제재 행 자체는 기록으로 남긴다.
 * 이 서비스는 트랜잭션을 열지 않고 갱신 문장마다 자동 커밋해서, 한 번에 오래 행을 잠그지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportContentCleanupService {

    /** 처리가 끝난 신고와 제재의 내용을 보관하는 기간이다. 달력 기준 1년이라 일수가 아니라 Period로 둔다. */
    static final Period RETENTION = Period.ofYears(1);

    // 한 번에 바꾸는 행 수의 상한이다. 한 번의 갱신이 너무 오래 행을 잠그지 않게 나눈다.
    static final int BATCH_SIZE = 1000;

    private final ReportContentCleanupMapper cleanupMapper;
    private final Clock clock;

    /**
     * 호출하면 신고와 제재의 내용을 BATCH_SIZE건씩 지우기를 대상이 없어질 때까지 되풀이하고, 지운 신고 수와 제재 수를 돌려준다.
     * 기준 시각은 지금(UTC)에서 1년을 뺀 시각이고, 정확히 1년 된 건은 대상에 든다.
     */
    public Result clearExpiredContent() {
        Instant cutoff =
                Instant.now(clock).atZone(ZoneOffset.UTC).minus(RETENTION).toInstant();
        int reports = repeatUntilDone(cutoff, cleanupMapper::clearHandledReportContent);
        int sanctions = repeatUntilDone(cutoff, cleanupMapper::clearFinishedSanctionReason);
        log.info("report content cleared reportCount={} sanctionCount={}", reports, sanctions);
        return new Result(reports, sanctions);
    }

    private static int repeatUntilDone(Instant cutoff, BiFunction<Instant, Integer, Integer> clearBatch) {
        int total = 0;
        int cleared;
        do {
            cleared = clearBatch.apply(cutoff, BATCH_SIZE);
            total += cleared;
        } while (cleared >= BATCH_SIZE);
        return total;
    }

    /** 내용을 지운 신고 수와 제재 수. */
    public record Result(int reports, int sanctions) {}
}
