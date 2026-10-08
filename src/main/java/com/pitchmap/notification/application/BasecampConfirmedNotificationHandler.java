package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 베이스캠프 확정 이벤트를 받아 멤버 전원에게 알림을 만든다. 확정 안내와 안전 수칙은 알림 한 건에 함께 담는다. */
@Component
class BasecampConfirmedNotificationHandler extends NotificationEventHandler<NotificationPayloads.BasecampMembers> {

    BasecampConfirmedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_CONFIRMED,
                NotificationPayloads.BasecampMembers.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.BasecampMembers payload) {
        return NotificationTemplates.confirmed(payload);
    }
}
