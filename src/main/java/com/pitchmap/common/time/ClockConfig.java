package com.pitchmap.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    // 시간에 의존하는 코드는 모두 이 빈을 주입받아야 한다. 그래야 테스트에서 시각을 바꿔 끼울 수 있다.
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
