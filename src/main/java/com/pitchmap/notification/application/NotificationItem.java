package com.pitchmap.notification.application;

import java.time.Instant;

/**
 * 알림함 목록의 알림 한 건이다. link는 연결할 화면이 없으면 null이고, readAt은 아직 읽지 않았으면 null이다.
 */
public record NotificationItem(
        long notificationId, String type, String title, String body, String link, Instant readAt, Instant createdAt) {}
