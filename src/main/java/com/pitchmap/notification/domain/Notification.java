package com.pitchmap.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 한 명의 알림함에 들어가는 알림 한 건. 회원은 다른 모듈의 엔티티라서 ID로만 가리킨다.
 * 같은 이벤트로 같은 회원에게 알림이 두 번 생기지 않도록 dedupKey는 DB에서 유일하다.
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    private String type;

    private String title;

    private String body;

    private String link;

    @Column(name = "dedup_key")
    private String dedupKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @Column(name = "created_at")
    private Instant createdAt;

    private Notification(
            long memberId, String type, String title, String body, String link, String dedupKey, Instant now) {
        this.memberId = memberId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
        this.dedupKey = dedupKey;
        this.createdAt = now;
    }

    /** 호출하면 안 읽은 상태의 알림을 만든다. link는 연결할 화면이 없으면 null이다. */
    public static Notification create(
            long memberId, String type, String title, String body, String link, String dedupKey, Instant now) {
        if (type == null || title == null || body == null || dedupKey == null || now == null) {
            throw new IllegalArgumentException("알림의 종류, 제목, 본문, 중복 방지 키, 생성 시각은 비어 있을 수 없습니다.");
        }
        return new Notification(memberId, type, title, body, link, dedupKey, now);
    }

    /** 호출하면 아직 읽지 않은 알림을 now에 읽은 것으로 바꾼다. 이미 읽었다면 처음 읽은 시각을 그대로 둔다. */
    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}
