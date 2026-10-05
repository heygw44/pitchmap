package com.pitchmap.common.external;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 외부 API 호출 수, 실패 수, 걸린 시간을 지표 하나로 남긴다.
 *
 * <p>공공데이터 API는 하루 호출 한도가 있어서, 운영자는 이 지표로 하루 호출 수와 실패 비율을 본다. 고캠핑 클라이언트뿐 아니라 날씨 같은
 * 다른 모듈의 외부 API 클라이언트도 이 클래스로 지표를 남긴다. 재시도도 한도를 쓰므로, 클라이언트는 HTTP 요청을 보낼 때마다 한 번씩 기록한다.
 *
 * <p>태그에는 API 이름, 작업 이름, 결과만 넣는다. 회원 ID나 요청 값을 넣으면 태그 조합이 끝없이 늘어나고 개인정보가 지표에 남기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class ExternalApiMetrics {

    private static final String TIMER_NAME = "pitchmap.external.api.requests";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final MeterRegistry meterRegistry;

    /**
     * {@code call}을 실행하고 걸린 시간을 기록한다. {@code call}이 예외를 던지면 결과를 실패로 기록하고 같은 예외를 그대로 던진다.
     *
     * @param api 외부 API 이름. 예: {@code gocamping}
     * @param operation 호출한 작업 이름. 예: {@code basedList}
     * @param call HTTP 요청 한 번을 보내고 응답을 해석하는 작업
     */
    public <T> T record(String api, String operation, Supplier<T> call) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = FAILURE;
        try {
            T result = call.get();
            outcome = SUCCESS;
            return result;
        } finally {
            sample.stop(timer(api, operation, outcome));
        }
    }

    private Timer timer(String api, String operation, String outcome) {
        return Timer.builder(TIMER_NAME)
                .description("외부 API에 보낸 HTTP 요청 수와 걸린 시간")
                .tag("api", api)
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}
