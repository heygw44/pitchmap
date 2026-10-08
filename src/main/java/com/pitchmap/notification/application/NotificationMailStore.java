package com.pitchmap.notification.application;

import com.pitchmap.notification.domain.NotificationRepository;
import com.pitchmap.notification.infra.NotificationMailMapper;
import com.pitchmap.notification.infra.NotificationMailRow;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 메일을 보내는 NotificationMailer는 메일 서버 응답을 기다리는 동안 DB 커넥션을 잡으면 안 된다.
// 그래서 메일러의 DB 접근은 모두 이 클래스의 짧은 트랜잭션 메서드로만 한다.
@Service
@RequiredArgsConstructor
class NotificationMailStore {

    private final NotificationMailMapper mailMapper;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /** 호출하면 dedupKeys에 해당하는 알림 가운데 지금 메일을 보낼 것을 알림 ID 순으로 돌려준다. */
    @Transactional(readOnly = true)
    List<NotificationMailRow> findTargets(Collection<String> dedupKeys) {
        if (dedupKeys.isEmpty()) {
            return List.of();
        }
        return mailMapper.selectMailTargets(dedupKeys, NotificationEventTypes.DEFAULT_EMAIL_ENABLED);
    }

    /** 호출하면 그 알림에 메일을 보낸 시각을 지금으로 기록한다. 이미 기록돼 있으면 그대로 둔다. */
    @Transactional
    void markSent(long notificationId) {
        notificationRepository.markEmailSent(notificationId, clock.instant());
    }
}
