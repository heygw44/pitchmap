package com.pitchmap.common.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    // 운영 clock 빈과 이름을 다르게 두고 @Primary로 이긴다. 같은 이름으로 덮어쓰면 빈 정의 오버라이딩을 켜야 한다.
    @Bean
    @Primary
    MutableClock mutableClock() {
        return MutableClock.atDefaultInstant();
    }
}
