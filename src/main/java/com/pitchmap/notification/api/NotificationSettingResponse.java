package com.pitchmap.notification.api;

import com.pitchmap.notification.application.NotificationSettingItem;

/** 알림 종류 하나의 이메일 수신 여부 응답이다. */
record NotificationSettingResponse(String type, boolean emailEnabled) {

    static NotificationSettingResponse from(NotificationSettingItem item) {
        return new NotificationSettingResponse(item.type(), item.emailEnabled());
    }
}
