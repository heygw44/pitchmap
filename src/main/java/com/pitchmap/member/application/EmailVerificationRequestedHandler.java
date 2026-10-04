package com.pitchmap.member.application;

import com.pitchmap.common.mail.MailSender;
import com.pitchmap.common.outbox.OutboxEventHandler;
import com.pitchmap.common.outbox.OutboxMessage;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증 코드 요청 이벤트를 받아 코드를 만들고 메일을 보낸다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 걸지 않는다. 코드 저장은 {@link EmailVerificationService#issueFor}가
 * 자기 짧은 트랜잭션으로 끝내고, 메일은 그 트랜잭션이 커밋된 뒤에 보낸다. 메일 서버 응답을 기다리는 동안 DB 커넥션을 잡지 않고,
 * 메일이 나간 코드는 반드시 DB에 있게 하려는 순서다. 메일 발송이 실패하면 예외가 발행기로 올라가 이벤트를 다시 처리한다.
 * 전달이 적어도 한 번이라 같은 이벤트가 두 번 오면 코드가 두 번 만들어지고, 가장 최근에 만든 코드만 유효하다.
 */
@Component
@RequiredArgsConstructor
class EmailVerificationRequestedHandler implements OutboxEventHandler {

    private final EmailVerificationService emailVerificationService;
    private final MailSender mailSender;
    private final JsonMapper jsonMapper;

    @Override
    public String eventType() {
        return EmailVerificationEvents.EVENT_TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        EmailVerificationEvents.Payload payload = parse(message);
        Optional<IssuedVerification> issued =
                emailVerificationService.issueFor(message.aggregateId(), payload.requestIp());
        issued.ifPresent(this::sendMail);
    }

    private void sendMail(IssuedVerification issued) {
        mailSender.send(EmailVerificationMailFactory.create(issued.email(), issued.code()));
    }

    // payload에는 접속 IP가 들어 있으므로 예외 메시지에 내용을 싣지 않는다.
    private EmailVerificationEvents.Payload parse(OutboxMessage message) {
        try {
            EmailVerificationEvents.Payload payload =
                    jsonMapper.readValue(message.payload(), EmailVerificationEvents.Payload.class);
            if (payload == null) {
                throw new IllegalArgumentException("이메일 인증 이벤트 payload가 비어 있습니다. eventId=" + message.eventId());
            }
            return payload;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("이메일 인증 이벤트 payload를 읽을 수 없습니다. eventId=" + message.eventId());
        }
    }
}
