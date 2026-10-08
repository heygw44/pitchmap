package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 멤버 변경 이벤트를 받아 남은 멤버 전원에게 알림을 만든다. */
@Component
class BasecampMemberChangedNotificationHandler extends NotificationEventHandler<NotificationPayloads.BasecampMembers> {

    BasecampMemberChangedNotificationHandler(
            JsonMapper jsonMapper, NotificationWriter notificationWriter, NotificationMailer notificationMailer) {
        super(
                NotificationEventTypes.BASECAMP_MEMBER_CHANGED,
                NotificationPayloads.BasecampMembers.class,
                jsonMapper,
                notificationWriter,
                notificationMailer);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.BasecampMembers payload) {
        return NotificationTemplates.memberChanged(payload);
    }
}
