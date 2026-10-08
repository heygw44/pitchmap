package com.pitchmap.notification.application;

import com.pitchmap.common.outbox.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 신고 처리 이벤트를 받아 신고자에게 처리 결과를 알린다. 제재 내용은 알림에 담지 않는다. */
@Component
class MemberReportResolvedNotificationHandler extends NotificationEventHandler<NotificationPayloads.ReportResolved> {

    MemberReportResolvedNotificationHandler(
            JsonMapper jsonMapper, NotificationWriter notificationWriter, NotificationMailer notificationMailer) {
        super(
                NotificationEventTypes.MEMBER_REPORT_RESOLVED,
                NotificationPayloads.ReportResolved.class,
                jsonMapper,
                notificationWriter,
                notificationMailer);
    }

    @Override
    List<NotificationDraft> drafts(OutboxMessage message, NotificationPayloads.ReportResolved payload) {
        return NotificationTemplates.reportResolved(payload);
    }
}
