package com.pitchmap.notification.api;

import com.pitchmap.notification.application.NotificationItem;
import com.pitchmap.notification.application.NotificationPage;
import java.time.Instant;
import java.util.List;

/** 알림함 목록의 한 페이지다. 전체 개수는 없고, 다음 페이지가 있는지만 hasNext로 알린다. */
public record NotificationPageResponse(List<Entry> content, int page, int size, boolean hasNext) {

    static NotificationPageResponse from(NotificationPage page) {
        List<Entry> content = page.content().stream().map(Entry::from).toList();
        return new NotificationPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record Entry(
            long notificationId,
            String type,
            String title,
            String body,
            String link,
            Instant readAt,
            Instant createdAt) {

        static Entry from(NotificationItem item) {
            return new Entry(
                    item.notificationId(),
                    item.type(),
                    item.title(),
                    item.body(),
                    item.link(),
                    item.readAt(),
                    item.createdAt());
        }
    }
}
