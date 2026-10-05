package com.pitchmap.common.testsupport;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

// 외부 API 대신 응답하는 WireMock 서버를 빈으로 둔다. 그러면 모든 통합 테스트가 Spring 컨텍스트 캐시와 함께 서버 하나를 재사용한다.
// 서버는 빈 포트를 골라 뜨고, 아래 등록기가 외부 API 기본 주소를 이 서버로 바꾼다.
// 테스트마다 다른 스텁이 섞이지 않도록 WireMockResetExtension이 테스트가 끝날 때마다 스텁과 요청 기록을 지운다.
@TestConfiguration(proxyBeanMethods = false)
public class WireMockTestConfig {

    @Bean(destroyMethod = "stop")
    WireMockServer wireMockServer() {
        WireMockServer server = new WireMockServer(options().dynamicPort());
        server.start();
        return server;
    }

    @Bean
    DynamicPropertyRegistrar wireMockPropertyRegistrar(WireMockServer wireMockServer) {
        return registry -> registry.add(
                "pitchmap.publicdata.gocamping.base-url", () -> wireMockServer.baseUrl() + "/B551011/GoCamping");
    }
}
