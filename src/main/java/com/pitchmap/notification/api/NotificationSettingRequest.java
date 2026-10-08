package com.pitchmap.notification.api;

import com.pitchmap.notification.application.NotificationSettingItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 알림 종류 하나의 이메일 수신 여부를 바꾸는 요청 항목이다. 알림 종류가 맞는지는 서비스가 검사한다. */
record NotificationSettingRequest(
        @NotBlank String type, @NotNull Boolean emailEnabled) {

    NotificationSettingItem toItem() {
        return new NotificationSettingItem(type, emailEnabled);
    }
}
