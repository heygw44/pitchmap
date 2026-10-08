package com.pitchmap.notification.application;

import java.util.List;

/** 알림함 목록의 한 페이지다. 전체 개수는 없고, 다음 페이지가 있는지만 hasNext로 알린다. */
public record NotificationPage(List<NotificationItem> content, int page, int size, boolean hasNext) {}
