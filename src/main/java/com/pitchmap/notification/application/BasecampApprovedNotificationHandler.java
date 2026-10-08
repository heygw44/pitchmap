package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 합류 승인 이벤트를 받아 신청자에게 알림을 만든다. */
@Component
class BasecampApprovedNotificationHandler extends NotificationEventHandler<NotificationPayloads.Decision> {

    BasecampApprovedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_APPROVED,
                NotificationPayloads.Decision.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.Decision payload) {
        return NotificationTemplates.approved(payload);
    }
}
