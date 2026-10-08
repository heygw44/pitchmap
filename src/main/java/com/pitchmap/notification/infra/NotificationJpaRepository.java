package com.pitchmap.notification.infra;

import com.pitchmap.notification.domain.Notification;
import com.pitchmap.notification.domain.NotificationRepository;
import com.pitchmap.notification.domain.NotificationSlice;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 도메인 리포지토리와 JpaRepository가 같은 이름의 메서드를 따로 선언해서, 구현체 타입으로 호출하면 모호해진다.
// 그래서 우리는 이 인터페이스에서 메서드를 다시 선언해 하나로 합친다.
public interface NotificationJpaRepository extends JpaRepository<Notification, Long>, NotificationRepository {

    @Override
    <S extends Notification> List<S> saveAll(Iterable<S> notifications);

    @Override
    Optional<Notification> findByIdAndMemberId(long id, long memberId);

    Slice<Notification> findByMemberIdOrderByIdDesc(long memberId, Pageable pageable);

    // Slice는 size + 1건을 읽어 다음 페이지가 있는지 판단하므로 전체 개수를 세는 쿼리를 보내지 않는다.
    @Override
    default NotificationSlice findPage(long memberId, int page, int size) {
        Slice<Notification> slice = findByMemberIdOrderByIdDesc(memberId, PageRequest.of(page, size));
        return new NotificationSlice(slice.getContent(), slice.hasNext());
    }

    @Override
    long countByMemberIdAndReadAtIsNull(long memberId);

    @Override
    @Query("SELECT n.dedupKey FROM Notification n WHERE n.dedupKey IN :dedupKeys")
    List<String> findExistingDedupKeys(@Param("dedupKeys") Collection<String> dedupKeys);

    // 한 번의 UPDATE로 바꾼다. 영속성 컨텍스트에 이 회원의 알림이 남아 있으면 예전 상태를 계속 쓰게 되므로 실행 뒤에 비운다.
    @Override
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.memberId = :memberId AND n.readAt IS NULL")
    int markAllRead(@Param("memberId") long memberId, @Param("now") Instant now);

    // 이미 보낸 알림은 건드리지 않도록 조건을 UPDATE에 둔다. 영속성 컨텍스트의 예전 값을 계속 쓰지 않도록 실행 뒤에 비운다.
    @Override
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.emailSentAt = :now WHERE n.id = :id AND n.emailSentAt IS NULL")
    int markEmailSent(@Param("id") long id, @Param("now") Instant now);
}
