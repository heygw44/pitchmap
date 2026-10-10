package com.pitchmap.common.testsupport;

import com.pitchmap.common.mail.TestMailSenderConfig;
import com.pitchmap.community.infra.TestCommunityImageStorageConfig;
import com.pitchmap.notification.application.RecordingOutboxEventHandler;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

// 이 어노테이션 하나에 설정을 모아서, 모든 통합 테스트가 같은 컨텍스트 캐시 키, MySQL 컨테이너, WireMock 서버를 공유하게 한다.
// 컨텍스트를 공유하므로, 확장이 테스트가 끝날 때마다 DB 행, 이동 가능한 시계, WireMock 스텁을 처음 상태로 되돌린다.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Import({
    MySqlTestContainerConfig.class,
    TestClockConfig.class,
    TestMailSenderConfig.class,
    TestCommunityImageStorageConfig.class,
    RecordingOutboxEventHandler.Config.class,
    WireMockTestConfig.class
})
@ExtendWith({
    DatabaseCleanupExtension.class,
    ClockResetExtension.class,
    WireMockResetExtension.class,
    CommunityImageStorageResetExtension.class
})
public @interface IntegrationTest {

    // 기본은 서버를 띄우지 않는 MOCK이다. Tomcat이 직접 처리하는 동작(프록시 헤더 등)을 확인하는 테스트만 RANDOM_PORT를 고른다.
    // 웹 환경이 다르면 Spring이 컨텍스트를 따로 띄우므로, 꼭 필요한 테스트에만 쓴다.
    @AliasFor(annotation = SpringBootTest.class, attribute = "webEnvironment")
    WebEnvironment webEnvironment() default WebEnvironment.MOCK;
}
