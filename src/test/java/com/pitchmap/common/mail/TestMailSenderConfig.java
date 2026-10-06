package com.pitchmap.common.mail;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** {@code @IntegrationTest}가 한 번만 가져와 모든 통합 테스트가 같은 컨텍스트 캐시를 쓰게 하는 설정이다. */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailSenderConfig {

    // 운영 SmtpMailSender 빈도 MailSender이므로, @Primary를 붙여 이 빈이 우선 주입되게 한다.
    // 이렇게 하면 운영 빈을 지우거나 덮어쓰지 않고도 테스트가 실제 SMTP 서버에 접속하지 않는다.
    @Bean
    @Primary
    TestMailSender testMailSender() {
        return new TestMailSender();
    }
}
