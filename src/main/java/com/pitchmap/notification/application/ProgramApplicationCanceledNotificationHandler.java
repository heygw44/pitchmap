package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 행사 신청 취소 이벤트를 받아 신청자에게 알림을 만든다. */
@Component
class ProgramApplicationCanceledNotificationHandler
        extends NotificationEventHandler<NotificationPayloads.ProgramApplicationCanceled> {

    ProgramApplicationCanceledNotificationHandler(
            JsonMapper jsonMapper, NotificationWriter notificationWriter, NotificationMailer notificationMailer) {
        super(
                NotificationEventTypes.PROGRAM_APPLICATION_CANCELED,
                NotificationPayloads.ProgramApplicationCanceled.class,
                jsonMapper,
                notificationWriter,
                notificationMailer);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.ProgramApplicationCanceled payload) {
        return NotificationTemplates.programApplicationCanceled(payload);
    }
}
