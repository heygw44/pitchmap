package com.pitchmap.notification.application;

import com.pitchmap.notification.domain.Notification;
import com.pitchmap.notification.domain.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이벤트 하나에서 만든 알림을 한 트랜잭션으로 저장한다.
 *
 * <p>아웃박스는 같은 이벤트를 적어도 한 번 전달한다. 그래서 알림마다 "{이벤트 ID}:{회원 ID}" 키를 만들고, 이미 저장된 키는 건너뛴다.
 * 조회와 저장 사이에 다른 발행기가 같은 키를 먼저 저장하면 DB의 유니크 제약이 위반 예외를 던진다.
 * 이 예외는 잡지 않고 그대로 올린다. 발행기가 이벤트를 다시 처리하면 이번에는 모든 키가 이미 있어서 아무것도 저장하지 않고 끝난다.
 */
@Service
@RequiredArgsConstructor
class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    /** 호출하면 drafts 가운데 아직 저장되지 않은 알림만 저장하고, 새로 저장한 건수를 돌려준다. */
    @Transactional
    int write(long eventId, List<NotificationDraft> drafts) {
        Map<String, NotificationDraft> draftsByKey = new LinkedHashMap<>();
        for (NotificationDraft draft : drafts) {
            draftsByKey.putIfAbsent(dedupKey(eventId, draft.memberId()), draft);
        }
        if (draftsByKey.isEmpty()) {
            return 0;
        }
        List<String> existingKeys = notificationRepository.findExistingDedupKeys(draftsByKey.keySet());
        existingKeys.forEach(draftsByKey::remove);
        Instant now = clock.instant();
        List<Notification> created = draftsByKey.entrySet().stream()
                .map(entry -> toNotification(entry.getKey(), entry.getValue(), now))
                .toList();
        notificationRepository.saveAll(created);
        return created.size();
    }

    static String dedupKey(long eventId, long memberId) {
        return eventId + ":" + memberId;
    }

    private static Notification toNotification(String dedupKey, NotificationDraft draft, Instant now) {
        return Notification.create(
                draft.memberId(), draft.type(), draft.title(), draft.body(), draft.link(), dedupKey, now);
    }
}
