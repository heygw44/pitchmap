package com.pitchmap.notification.application;

import com.pitchmap.notification.domain.Notification;
import com.pitchmap.notification.domain.NotificationRepository;
import com.pitchmap.notification.domain.NotificationSlice;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인한 회원의 알림함을 읽는다. */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    /**
     * 호출하면 memberId인 회원의 알림을 최신순으로 한 페이지 읽는다.
     * 전체 개수는 세지 않고, 다음 페이지가 있는지만 함께 돌려준다.
     */
    @Transactional(readOnly = true)
    public NotificationPage list(long memberId, int page, int size) {
        NotificationSlice slice = notificationRepository.findPage(memberId, page, size);
        List<NotificationItem> content =
                slice.content().stream().map(NotificationQueryService::toItem).toList();
        return new NotificationPage(content, page, size, slice.hasNext());
    }

    /** 호출하면 memberId인 회원의 안 읽은 알림 수를 돌려준다. */
    @Transactional(readOnly = true)
    public long countUnread(long memberId) {
        return notificationRepository.countByMemberIdAndReadAtIsNull(memberId);
    }

    private static NotificationItem toItem(Notification notification) {
        return new NotificationItem(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
