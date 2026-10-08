package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxEventHandler;
import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 이벤트를 알림으로 바꿔 저장하고 메일로도 보내는 처리기의 공통 흐름이다. payload를 읽고, 하위 클래스가 만든 알림 내용을
 * {@link NotificationWriter}에 넘겨 저장한 다음, 그 알림 가운데 메일을 아직 보내지 않은 것을 {@link NotificationMailer}로 보낸다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 걸지 않는다. 저장은 {@link NotificationWriter}가 자기 트랜잭션으로 끝내고,
 * 메일은 그 트랜잭션이 커밋된 뒤에 보낸다. 메일 서버 응답을 기다리는 동안 DB 커넥션을 잡지 않고, 메일이 나간 알림은 반드시 DB에 있게 하려는 순서다.
 * payload를 읽지 못하거나 저장·메일 발송에 실패하면 예외가 발행기로 올라가 이벤트를 다시 처리한다. 같은 이벤트가 다시 와도
 * {@link NotificationWriter}가 이미 저장된 알림을 건너뛰므로 알림은 한 번만 생기고, {@link NotificationMailer}가 메일을 보낸 알림을
 * 건너뛰므로 메일은 보내지 못한 알림에만 다시 나간다.
 */
abstract class NotificationEventHandler<P> implements OutboxEventHandler {

    private final String eventType;
    private final Class<P> payloadType;
    private final JsonMapper jsonMapper;
    private final NotificationWriter notificationWriter;
    private final NotificationMailer notificationMailer;

    NotificationEventHandler(
            String eventType,
            Class<P> payloadType,
            JsonMapper jsonMapper,
            NotificationWriter notificationWriter,
            NotificationMailer notificationMailer) {
        this.eventType = eventType;
        this.payloadType = payloadType;
        this.jsonMapper = jsonMapper;
        this.notificationWriter = notificationWriter;
        this.notificationMailer = notificationMailer;
    }

    @Override
    public String eventType() {
        return eventType;
    }

    @Override
    public void handle(OutboxMessage message) {
        P payload = parse(message);
        List<NotificationDraft> drafts = drafts(message, payload);
        notificationWriter.write(message.eventId(), drafts);
        List<String> dedupKeys = drafts.stream()
                .map(draft -> NotificationWriter.dedupKey(message.eventId(), draft.memberId()))
                .distinct()
                .toList();
        notificationMailer.sendPending(dedupKeys);
    }

    /** 이벤트에서 받는 사람마다 알림 내용을 만든다. message는 payload에 없는 값(예: 집합체 ID)이 필요할 때 쓴다. */
    abstract List<NotificationDraft> drafts(OutboxMessage message, P payload);

    // payload에는 회원 ID가 들어 있으므로 예외 메시지에 내용을 싣지 않는다.
    private P parse(OutboxMessage message) {
        try {
            P payload = jsonMapper.readValue(message.payload(), payloadType);
            if (payload == null) {
                throw new IllegalArgumentException(eventType + " 이벤트 payload가 비어 있습니다. eventId=" + message.eventId());
            }
            return payload;
        } catch (JacksonException e) {
            throw new IllegalArgumentException(eventType + " 이벤트 payload를 읽을 수 없습니다. eventId=" + message.eventId());
        }
    }
}
