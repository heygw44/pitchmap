package com.pitchmap.notification.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    <S extends Notification> List<S> saveAll(Iterable<S> notifications);

    /** 호출하면 그 회원의 알림 중 memberId와 id가 모두 맞는 것을 돌려준다. 다른 회원의 알림이면 비어 있다. */
    Optional<Notification> findByIdAndMemberId(long id, long memberId);

    /**
     * 호출하면 그 회원의 알림을 id가 큰 순서(최신순)로 한 페이지 읽는다. page는 0부터 시작한다.
     * 전체 개수를 세지 않고, 다음 페이지가 있는지만 함께 돌려준다.
     */
    NotificationSlice findPage(long memberId, int page, int size);

    long countByMemberIdAndReadAtIsNull(long memberId);

    /** 호출하면 주어진 키 중 이미 저장된 중복 방지 키만 돌려준다. */
    List<String> findExistingDedupKeys(Collection<String> dedupKeys);

    /** 호출하면 그 회원의 안 읽은 알림을 모두 now에 읽은 것으로 바꾸고, 바뀐 건수를 돌려준다. */
    int markAllRead(long memberId, Instant now);
}
