package com.pitchmap.common.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// 설정을 메타 어노테이션 하나로 모아 모든 통합 테스트가 같은 컨텍스트 캐시 키와 MySQL 컨테이너를 공유하게 한다.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
public @interface IntegrationTest {}
