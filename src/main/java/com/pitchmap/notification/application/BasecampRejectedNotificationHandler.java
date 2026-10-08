package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 합류 거절 이벤트를 받아 신청자에게 알림을 만든다. */
@Component
class BasecampRejectedNotificationHandler extends NotificationEventHandler<NotificationPayloads.Decision> {

    BasecampRejectedNotificationHandler(
            JsonMapper jsonMapper, NotificationWriter notificationWriter, NotificationMailer notificationMailer) {
        super(
                NotificationEventTypes.BASECAMP_REJECTED,
                NotificationPayloads.Decision.class,
                jsonMapper,
                notificationWriter,
                notificationMailer);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.Decision payload) {
        return NotificationTemplates.rejected(payload);
    }
}
