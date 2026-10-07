package com.pitchmap.common.scheduling;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 도는 작업(동기화, 정리 작업 등)이 실패한 횟수를 센다.
 *
 * <p>스케줄 작업은 예외를 잡아 ERROR 로그만 남기고 다음 주기를 기다린다. 그래서 Spring이 기본으로 남기는 스케줄 실행 지표에는 실패가 성공으로
 * 기록된다. 운영자가 실패를 알림으로 받을 수 있도록, 각 작업은 예외를 잡는 자리에서 이 지표를 한 번 기록한다.
 *
 * <p>각 작업은 생성될 때 {@link #register}로 실패 수를 0으로 먼저 만들어 둔다. 실패할 때 처음 만들면 첫 값이 1이 되는데, Prometheus는
 * 처음 나타난 시계열의 첫 값을 증가로 세지 않아서 앱이 시작된 뒤 첫 실패가 알림에서 빠지기 때문이다.
 *
 * <p>태그에는 작업 이름만 넣는다. 예외 메시지를 넣으면 태그 값이 끝없이 늘어나기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class ScheduledJobMetrics {

    private static final String FAILURE_COUNTER_NAME = "pitchmap.scheduled.job.failures";

    private final MeterRegistry meterRegistry;

    /**
     * 작업의 실패 수를 0으로 만들어 둔다. 이미 있으면 그대로 둔다.
     *
     * @param job 작업 이름. 예: {@code gocamping-sync}
     */
    public void register(String job) {
        failureCounter(job);
    }

    /**
     * 작업 한 번이 실패했다고 기록한다.
     *
     * @param job 작업 이름. 예: {@code gocamping-sync}
     */
    public void recordFailure(String job) {
        failureCounter(job).increment();
    }

    private Counter failureCounter(String job) {
        return Counter.builder(FAILURE_COUNTER_NAME)
                .description("스케줄 작업이 예외로 끝난 횟수")
                .tag("job", job)
                .register(meterRegistry);
    }
}
