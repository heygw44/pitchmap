package com.pitchmap.common.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    // 이 빈은 운영 clock 빈과 이름을 다르게 짓고 @Primary를 붙여 우선 주입되게 한다.
    // 같은 이름으로 덮어쓰려면 빈 정의 오버라이딩을 켜야 하기 때문이다.
    @Bean
    @Primary
    MutableClock mutableClock() {
        return MutableClock.atDefaultInstant();
    }
}
