package com.pitchmap.community.infra;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** {@code @IntegrationTest}가 한 번만 가져와 모든 통합 테스트가 같은 컨텍스트 캐시를 쓰게 하는 설정이다. */
@TestConfiguration(proxyBeanMethods = false)
public class TestCommunityImageStorageConfig {

    // 로컬용 가짜 저장소 빈도 CommunityImageStorage이므로, @Primary를 붙여 이 빈이 우선 주입되게 한다.
    @Bean
    @Primary
    TestCommunityImageStorage testCommunityImageStorage() {
        return new TestCommunityImageStorage();
    }
}
