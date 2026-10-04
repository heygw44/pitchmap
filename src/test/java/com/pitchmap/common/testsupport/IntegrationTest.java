package com.pitchmap.common.testsupport;

import com.pitchmap.common.mail.TestMailSenderConfig;
import com.pitchmap.notification.application.RecordingOutboxEventHandler;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// 이 어노테이션 하나에 설정을 모아서, 모든 통합 테스트가 같은 컨텍스트 캐시 키와 MySQL 컨테이너를 공유하게 한다.
// 컨텍스트를 공유하므로, 확장이 테스트가 끝날 때마다 DB 행과 이동 가능한 시계를 처음 상태로 되돌린다.
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
    RecordingOutboxEventHandler.Config.class
})
@ExtendWith({DatabaseCleanupExtension.class, ClockResetExtension.class})
public @interface IntegrationTest {}
