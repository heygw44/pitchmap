package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 합류 신청 이벤트를 받아 캠프 리더에게 알림을 만든다. 이벤트의 집합체 ID가 베이스캠프 ID다. */
@Component
class BasecampAppliedNotificationHandler extends NotificationEventHandler<NotificationPayloads.Applied> {

    BasecampAppliedNotificationHandler(JsonMapper jsonMapper, NotificationWriter notificationWriter) {
        super(
                NotificationEventTypes.BASECAMP_APPLIED,
                NotificationPayloads.Applied.class,
                jsonMapper,
                notificationWriter);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.Applied payload) {
        return NotificationTemplates.applied(message.aggregateId(), payload);
    }
}
