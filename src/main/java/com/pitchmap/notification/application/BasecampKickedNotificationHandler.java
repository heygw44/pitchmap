package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 강퇴 이벤트를 받아 강퇴된 회원에게 알림을 만든다. */
@Component
class BasecampKickedNotificationHandler extends NotificationEventHandler<NotificationPayloads.Kicked> {

    BasecampKickedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_KICKED,
                NotificationPayloads.Kicked.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.Kicked payload) {
        return NotificationTemplates.kicked(payload);
    }
}
