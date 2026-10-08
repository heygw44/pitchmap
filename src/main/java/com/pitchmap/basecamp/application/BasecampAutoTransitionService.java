package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.infra.BasecampAutoTransitionMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 출발 전날 0시(한국 시간)가 된 베이스캠프를 확정하거나 취소하고, 종료일이 지난 확정 베이스캠프를 완료한다.
 *
 * <p>이 클래스는 트랜잭션을 열지 않는다. 대상 ID를 나눠 읽은 뒤 베이스캠프마다 {@link BasecampAutoTransitionApplier}를 불러
 * 베이스캠프 하나를 트랜잭션 하나로 처리한다. 한 베이스캠프가 실패해도 나머지는 계속 처리하고, 실패한 것은 다음 실행에서 다시 집는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BasecampAutoTransitionService {

    // 한 번에 읽는 베이스캠프 ID의 수다. 밀린 베이스캠프가 많아도 목록이 너무 커지지 않게 나눈다.
    static final int BATCH_SIZE = 500;

    private final BasecampAutoTransitionMapper autoTransitionMapper;
    private final BasecampAutoTransitionApplier applier;
    private final Clock clock;

    /**
     * 호출하면 완료할 베이스캠프를 먼저 처리하고, 이어서 출발 전날 0시가 지난 베이스캠프를 처리한다.
     * 출발일이 내일 이하이면 대상이다. 그래서 어제 실행하지 못해 출발일이 오늘이 된 베이스캠프도 이번에 처리한다.
     */
    public Result run() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, Basecamp.KOREA);
        int completed = processAll(
                (afterId, limit) -> autoTransitionMapper.selectDueForCompletionIds(today, afterId, limit),
                basecampId -> applier.complete(basecampId, today, now));
        LocalDate dueStartDate = today.plusDays(1);
        int departed = processAll(
                (afterId, limit) -> autoTransitionMapper.selectDueForDepartureIds(dueStartDate, afterId, limit),
                basecampId -> applier.processDayBeforeDeparture(basecampId, now));
        log.info("basecamp auto transition completed={} departureProcessed={}", completed, departed);
        return new Result(completed, departed);
    }

    private int processAll(BiFunction<Long, Integer, List<Long>> selector, BasecampProcessor processor) {
        int processed = 0;
        long afterId = 0;
        while (true) {
            List<Long> ids = selector.apply(afterId, BATCH_SIZE);
            for (long basecampId : ids) {
                if (processOne(processor, basecampId)) {
                    processed++;
                }
            }
            if (ids.size() < BATCH_SIZE) {
                return processed;
            }
            afterId = ids.get(ids.size() - 1);
        }
    }

    // 한 베이스캠프의 실패가 다른 베이스캠프의 처리를 막지 않게 여기서 잡는다. 실패한 베이스캠프는 상태가 그대로라 다음 실행의 대상이 된다.
    private boolean processOne(BasecampProcessor processor, long basecampId) {
        try {
            return processor.process(basecampId);
        } catch (RuntimeException e) {
            log.error("basecamp auto transition failed basecampId={}", basecampId, e);
            return false;
        }
    }

    @FunctionalInterface
    private interface BasecampProcessor {

        boolean process(long basecampId);
    }

    /**
     * @param completed 이번 실행에서 완료한 베이스캠프 수
     * @param departureProcessed 이번 실행에서 확정하거나 취소한 베이스캠프 수
     */
    public record Result(int completed, int departureProcessed) {}
}
