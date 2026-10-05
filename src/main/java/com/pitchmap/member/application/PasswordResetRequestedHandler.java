package com.pitchmap.member.application;

import com.pitchmap.common.mail.MailSender;
import com.pitchmap.common.outbox.OutboxEventHandler;
import com.pitchmap.common.outbox.OutboxMessage;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 재설정 요청 이벤트를 받아 토큰을 만들고 메일을 보낸다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 걸지 않는다. 토큰 저장은 {@link PasswordResetRequestService#issueFor}가
 * 자기 짧은 트랜잭션으로 끝내고, 메일은 그 트랜잭션이 커밋된 뒤에 보낸다. 메일 서버 응답을 기다리는 동안 DB 커넥션을 잡지 않고,
 * 메일이 나간 토큰은 반드시 DB에 있게 하려는 순서다. 메일 발송이 실패하면 예외가 발행기로 올라가 이벤트를 다시 처리한다.
 * 전달이 적어도 한 번이라 같은 이벤트가 두 번 오면 토큰이 두 개 만들어지고, 각각 독립적으로 쓸 수 있다.
 */
@Component
class PasswordResetRequestedHandler implements OutboxEventHandler {

    private static final String LINK_PATH = "/password-reset#token=";

    private final PasswordResetRequestService passwordResetRequestService;
    private final MailSender mailSender;
    private final String baseUrl;

    PasswordResetRequestedHandler(
            PasswordResetRequestService passwordResetRequestService,
            MailSender mailSender,
            @Value("${pitchmap.web.base-url}") String baseUrl) {
        this.passwordResetRequestService = passwordResetRequestService;
        this.mailSender = mailSender;
        this.baseUrl = stripTrailingSlashes(baseUrl);
    }

    @Override
    public String eventType() {
        return PasswordResetEvents.EVENT_TYPE;
    }

    // payload에는 싣는 값이 없어서 읽지 않는다.
    @Override
    public void handle(OutboxMessage message) {
        Optional<IssuedPasswordReset> issued = passwordResetRequestService.issueFor(message.aggregateId());
        issued.ifPresent(this::sendMail);
    }

    private void sendMail(IssuedPasswordReset issued) {
        mailSender.send(PasswordResetMailFactory.create(issued.email(), link(issued.token())));
    }

    // 토큰을 쿼리 문자열이 아니라 프래그먼트(#)에 싣는다. 브라우저는 프래그먼트를 서버로 보내지 않아서
    // 접근 로그, 프록시 로그, Referer에 토큰이 남지 않는다.
    private String link(String token) {
        return baseUrl + LINK_PATH + token;
    }

    private static String stripTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
