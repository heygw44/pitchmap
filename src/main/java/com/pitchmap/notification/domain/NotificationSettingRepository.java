package com.pitchmap.notification.domain;

import java.util.List;

public interface NotificationSettingRepository {

    /** 호출하면 그 회원이 저장해 둔 설정을 모두 돌려준다. 설정을 저장하지 않은 종류는 포함하지 않는다. */
    List<NotificationSetting> findByMemberId(long memberId);

    /** 호출하면 그 회원의 설정을 모두 지운다. */
    void deleteByMemberId(long memberId);

    <S extends NotificationSetting> List<S> saveAll(Iterable<S> settings);
}
