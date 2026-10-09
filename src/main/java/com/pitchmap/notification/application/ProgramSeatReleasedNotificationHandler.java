package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 행사 빈자리 이벤트를 받아 빈자리 알림을 신청한 회원에게 알림을 만든다. */
@Component
class ProgramSeatReleasedNotificationHandler
        extends NotificationEventHandler<NotificationPayloads.ProgramSeatReleased> {

    ProgramSeatReleasedNotificationHandler(
            JsonMapper jsonMapper, NotificationWriter notificationWriter, NotificationMailer notificationMailer) {
        super(
                NotificationEventTypes.PROGRAM_SEAT_RELEASED,
                NotificationPayloads.ProgramSeatReleased.class,
                jsonMapper,
                notificationWriter,
                notificationMailer);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.ProgramSeatReleased payload) {
        return NotificationTemplates.programSeatReleased(payload);
    }
}
