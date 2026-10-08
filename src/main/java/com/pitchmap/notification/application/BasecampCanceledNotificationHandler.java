package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 베이스캠프 취소 이벤트를 받아 멤버 전원에게 알림을 만든다. */
@Component
class BasecampCanceledNotificationHandler extends NotificationEventHandler<NotificationPayloads.BasecampMembers> {

    BasecampCanceledNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_CANCELED,
                NotificationPayloads.BasecampMembers.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.BasecampMembers payload) {
        return NotificationTemplates.canceled(payload);
    }
}
