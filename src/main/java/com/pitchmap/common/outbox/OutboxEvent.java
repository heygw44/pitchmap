package com.pitchmap.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 원래 작업과 같은 트랜잭션에 기록하는 이벤트 행이다. JPA는 기록(INSERT)에만 쓴다.
 * 발행 상태, 시도 횟수, 임대 시각은 발행기가 MyBatis 조건부 UPDATE로 바꾸므로 이 엔티티에는 변경 메서드가 없다.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "aggregate_type")
    private String aggregateType;

    @Column(name = "aggregate_id")
    private long aggregateId;

    // 문자열을 그대로 JSON 컬럼에 넣는다. Hibernate는 String 속성의 값을 다시 인용부호로 감싸지 않는다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxEventStatus status;

    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private OutboxEvent(String eventType, String aggregateType, long aggregateId, String payload, Instant now) {
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.attemptCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static OutboxEvent pending(
            String eventType, String aggregateType, long aggregateId, String payloadJson, Instant now) {
        requireText(eventType, "eventType");
        requireText(aggregateType, "aggregateType");
        requireText(payloadJson, "payloadJson");
        if (now == null) {
            throw new IllegalArgumentException("이벤트를 기록한 시각이 null입니다.");
        }
        return new OutboxEvent(eventType, aggregateType, aggregateId, payloadJson, now);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("이벤트 " + name + " 값이 비어 있습니다.");
        }
    }
}
