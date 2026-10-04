package com.pitchmap.common.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

// 컨테이너를 빈으로 두면 같은 설정을 쓰는 통합 테스트가 Spring 컨텍스트 캐시와 함께 컨테이너 하나를 재사용한다.
// @Testcontainers/@Container는 클래스마다 컨테이너를 멈춰서 캐시된 컨텍스트와 충돌한다.
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestContainerConfig {

    @Bean
    @ServiceConnection
    MySQLContainer mysql() {
        return new MySQLContainer("mysql:8.4");
    }
}
