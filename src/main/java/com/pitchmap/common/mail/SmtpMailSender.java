package com.pitchmap.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP로 메일을 보낸다. 연결·읽기·쓰기 제한 시간은 {@code spring.mail.properties.mail.smtp.*} 설정이 정한다.
 *
 * <p>발신 주소는 {@code pitchmap.mail.from}이다. 네이버 SMTP는 발신 주소가 인증한 계정과 다르면 메일을 거부하므로,
 * 설정이 없으면 SMTP 계정({@code spring.mail.username})을 쓴다.
 *
 * <p>실패 원인에는 수신자 주소가 들어 있을 수 있어서, 예외 메시지에는 원인의 클래스 이름만 남긴다.
 */
@Component
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;
    private final String from;

    public SmtpMailSender(JavaMailSender javaMailSender, @Value("${pitchmap.mail.from}") String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Override
    public void send(MailMessage message) {
        try {
            javaMailSender.send(toMimeMessage(message));
        } catch (MessagingException | MailException e) {
            throw new MailDeliveryException(
                    "mail delivery failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private MimeMessage toMimeMessage(MailMessage message) throws MessagingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(message.to());
        helper.setSubject(message.subject());
        helper.setText(message.body(), false);
        return mimeMessage;
    }
}
