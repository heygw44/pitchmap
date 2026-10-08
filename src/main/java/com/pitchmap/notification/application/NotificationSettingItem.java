package com.pitchmap.notification.application;

/**
 * 알림 종류 하나의 이메일 수신 여부다.
 *
 * @param type 알림 종류. 알림을 만든 이벤트 종류 이름과 같다
 * @param emailEnabled 이메일로도 받으면 true
 */
public record NotificationSettingItem(String type, boolean emailEnabled) {}
