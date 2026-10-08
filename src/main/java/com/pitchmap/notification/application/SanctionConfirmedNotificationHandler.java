package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 제재 확정 이벤트를 받아 제재받은 회원에게 알림을 만든다. 경고도 알린다. */
@Component
class SanctionConfirmedNotificationHandler extends NotificationEventHandler<NotificationPayloads.SanctionConfirmed> {

    SanctionConfirmedNotificationHandler(
            JsonMapper jsonMapper, NotificationWriter notificationWriter, NotificationMailer notificationMailer) {
        super(
                NotificationEventTypes.SANCTION_CONFIRMED,
                NotificationPayloads.SanctionConfirmed.class,
                jsonMapper,
                notificationWriter,
                notificationMailer);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.SanctionConfirmed payload) {
        return NotificationTemplates.sanctionConfirmed(payload);
    }
}
