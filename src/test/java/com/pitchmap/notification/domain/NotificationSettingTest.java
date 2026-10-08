package com.pitchmap.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationSettingTest {

    @Test
    @DisplayName("[F-20] 설정을 만들면 회원 ID와 알림 종류가 기본 키가 되고 새 엔티티로 취급한다")
    void createsWithCompositeKey() {
        NotificationSetting setting = NotificationSetting.of(7L, "BASECAMP_CONFIRMED", false);

        assertThat(setting.getId()).isEqualTo(new NotificationSettingId(7L, "BASECAMP_CONFIRMED"));
        assertThat(setting.isEmailEnabled()).isFalse();
        assertThat(setting.isNew()).isTrue();
    }

    @Test
    @DisplayName("[F-20] 알림 종류가 null이면 IllegalArgumentException을 던진다")
    void rejectsNullType() {
        assertThatThrownBy(() -> NotificationSetting.of(7L, null, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
