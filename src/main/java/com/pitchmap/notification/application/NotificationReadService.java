package com.pitchmap.notification.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.notification.domain.Notification;
import com.pitchmap.notification.domain.NotificationRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인한 회원이 자기 알림을 읽음으로 바꾼다. */
@Service
@RequiredArgsConstructor
public class NotificationReadService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 알림 한 건을 읽음으로 바꾼다. 이미 읽은 알림이면 처음 읽은 시각을 그대로 둔다.
     * 없는 알림이거나 다른 회원의 알림이면 존재를 드러내지 않으려고 같은 NOT_FOUND 예외를 던진다.
     */
    @Transactional
    public void markRead(long memberId, long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        notification.markRead(clock.instant());
    }

    /** 호출하면 memberId인 회원의 안 읽은 알림을 모두 읽음으로 바꾼다. 이미 읽은 알림의 읽은 시각은 건드리지 않는다. */
    @Transactional
    public void markAllRead(long memberId) {
        notificationRepository.markAllRead(memberId, clock.instant());
    }
}
