package com.pitchmap.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpMailSenderTest {

    private static final String FROM = "pitchmap@example.com";
    private static final String RECIPIENT = "hiker@example.com";
    private static final String SUBJECT = "[피치맵] 이메일 인증 코드";
    private static final String BODY = "인증 코드는 123456입니다.";

    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);
    private final SmtpMailSender mailSender = new SmtpMailSender(javaMailSender, FROM);

    @BeforeEach
    void stubMimeMessageCreation() {
        // 메시지 객체는 실제 구현이 만든 것을 쓴다. 보낸 메시지의 헤더와 본문을 그대로 읽어 확인하려는 것이다.
        JavaMailSenderImpl messageFactory = new JavaMailSenderImpl();
        given(javaMailSender.createMimeMessage()).willAnswer(invocation -> messageFactory.createMimeMessage());
    }

    @Test
    @DisplayName("[F-01] 발신자, 수신자, 제목, 본문을 담은 메일을 JavaMailSender로 보낸다")
    void sendsMessageWithFromToSubjectAndBody() throws Exception {
        mailSender.send(new MailMessage(RECIPIENT, SUBJECT, BODY));

        MimeMessage sent = captureSentMessage();
        assertThat(sent.getFrom()).containsExactly(new InternetAddress(FROM));
        assertThat(sent.getRecipients(Message.RecipientType.TO)).containsExactly(new InternetAddress(RECIPIENT));
        assertThat(sent.getSubject()).isEqualTo(SUBJECT);
        assertThat(sent.getContent()).isEqualTo(BODY);
    }

    @Test
    @DisplayName("[F-01] 제목과 본문을 UTF-8로 인코딩하고 한글이 그대로 복원된다")
    void encodesSubjectAndBodyAsUtf8() throws Exception {
        mailSender.send(new MailMessage(RECIPIENT, SUBJECT, BODY));

        MimeMessage sent = captureSentMessage();
        sent.saveChanges();
        assertThat(sent.getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat(sent.getContent()).isNotInstanceOf(MimeMultipart.class);
        assertThat(sent.getHeader("Subject", null)).contains("=?UTF-8?");
        assertThat(sent.getSubject()).isEqualTo(SUBJECT);
    }

    @Test
    @DisplayName("[F-01] 전송이 실패하면 원인을 유지한 MailDeliveryException을 던지고, 메시지에 수신자와 본문을 담지 않는다")
    void wrapsTransportFailureWithoutRecipientInMessage() {
        MailSendException transportFailure = new MailSendException("550 5.1.1 <" + RECIPIENT + "> user unknown");
        willThrow(transportFailure).given(javaMailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> mailSender.send(new MailMessage(RECIPIENT, SUBJECT, BODY)))
                .isInstanceOf(MailDeliveryException.class)
                .hasCause(transportFailure)
                .message()
                .doesNotContain(RECIPIENT)
                .doesNotContain(BODY);
    }

    @Test
    @DisplayName("[F-01] 수신자 주소 형식이 잘못되어도 MailDeliveryException으로 바꿔 던지고, 메시지에 수신자를 담지 않는다")
    void wrapsInvalidRecipientAsDeliveryFailure() {
        String invalidRecipient = "not an address@@";

        assertThatThrownBy(() -> mailSender.send(new MailMessage(invalidRecipient, SUBJECT, BODY)))
                .isInstanceOf(MailDeliveryException.class)
                .message()
                .doesNotContain(invalidRecipient);
    }

    @Test
    @DisplayName("MailMessage의 toString은 수신자와 본문을 드러내지 않는다")
    void mailMessageToStringHidesValues() {
        String text = new MailMessage(RECIPIENT, SUBJECT, BODY).toString();

        assertThat(text).doesNotContain(RECIPIENT).doesNotContain(BODY);
    }

    private MimeMessage captureSentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }
}
