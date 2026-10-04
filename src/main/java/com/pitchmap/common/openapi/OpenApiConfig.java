package com.pitchmap.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI pitchmapOpenApi() {
        return new OpenAPI()
                .info(new Info().title("피치맵 API").version("v1").description("백패커가 합법적인 박지를 찾고 믿을 수 있는 동행과 함께 가는 서비스"));
    }
}
