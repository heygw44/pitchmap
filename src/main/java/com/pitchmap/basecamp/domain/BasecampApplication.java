package com.pitchmap.basecamp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 베이스캠프에 보낸 합류 신청이다. 한 회원은 한 베이스캠프에 행을 하나만 가진다.
 *
 * <p>신청자가 취소한 뒤 다시 신청하면 새 행을 만들지 않고 같은 행을 대기 상태로 되돌린다.
 * 상태를 바꾸는 메서드는 같은 패키지의 {@link Basecamp}만 부른다.
 */
@Entity
@Table(name = "basecamp_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BasecampApplication {

    /** 신청 메시지의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int MESSAGE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "basecamp_id")
    private Basecamp basecamp;

    @Column(name = "applicant_id")
    private long applicantId;

    private String message;

    @Enumerated(EnumType.STRING)
    private BasecampApplicationStatus status;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private BasecampApplication(Basecamp basecamp, long applicantId, String message, Instant now) {
        this.basecamp = basecamp;
        this.applicantId = applicantId;
        this.message = message;
        this.status = BasecampApplicationStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    static BasecampApplication submit(Basecamp basecamp, long applicantId, String message, Instant now) {
        requireValidMessage(message);
        return new BasecampApplication(basecamp, applicantId, message, now);
    }

    public boolean isFrom(long memberId) {
        return applicantId == memberId;
    }

    public boolean isPending() {
        return status == BasecampApplicationStatus.PENDING;
    }

    void approve(Instant now) {
        this.status = BasecampApplicationStatus.APPROVED;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    void reject(Instant now) {
        this.status = BasecampApplicationStatus.REJECTED;
        this.decidedAt = now;
        this.updatedAt = now;
    }

    void cancel(Instant now) {
        this.status = BasecampApplicationStatus.CANCELED;
        this.updatedAt = now;
    }

    void expire(Instant now) {
        this.status = BasecampApplicationStatus.EXPIRED;
        this.updatedAt = now;
    }

    // 취소했던 신청자가 다시 신청하는 경우다. 이전 결정 시각은 의미가 없어져서 비운다.
    void resubmit(String message, Instant now) {
        requireValidMessage(message);
        this.message = message;
        this.status = BasecampApplicationStatus.PENDING;
        this.decidedAt = null;
        this.updatedAt = now;
    }

    private static void requireValidMessage(String message) {
        if (message != null && message.length() > MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException("신청 메시지는 " + MESSAGE_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
