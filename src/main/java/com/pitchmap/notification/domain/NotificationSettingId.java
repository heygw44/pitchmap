package com.pitchmap.notification.domain;

import java.io.Serializable;

/** {@link NotificationSetting}의 복합 기본 키. 회원 ID와 알림 종류다. */
public record NotificationSettingId(long memberId, String type) implements Serializable {}
