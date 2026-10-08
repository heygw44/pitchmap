package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 베이스캠프 완료 이벤트를 받아 멤버 전원에게 알림을 만든다. 완료 안내와 동행 후기 요청은 알림 한 건에 함께 담는다. */
@Component
class BasecampCompletedNotificationHandler extends NotificationEventHandler<NotificationPayloads.BasecampMembers> {

    BasecampCompletedNotificationHandler(
            JsonMapper jsonMapper, NotificationWriter notificationWriter, NotificationMailer notificationMailer) {
        super(
                NotificationEventTypes.BASECAMP_COMPLETED,
                NotificationPayloads.BasecampMembers.class,
                jsonMapper,
                notificationWriter,
                notificationMailer);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.BasecampMembers payload) {
        return NotificationTemplates.completed(payload);
    }
}
