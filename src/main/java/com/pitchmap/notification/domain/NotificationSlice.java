package com.pitchmap.notification.domain;

import java.util.List;

/** 알림 목록의 한 페이지다. hasNext는 이 페이지 뒤에 알림이 더 있는지를 뜻한다. */
public record NotificationSlice(List<Notification> content, boolean hasNext) {}
