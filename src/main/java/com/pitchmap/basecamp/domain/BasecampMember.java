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
 * 베이스캠프에 들어온 멤버다. 캠프 리더도 한 행으로 들어 있다.
 *
 * <p>탈퇴하거나 강퇴돼도 행을 지우지 않는다. 그래서 그 회원은 같은 베이스캠프에 다시 들어올 수 없다.
 * 상태를 바꾸는 메서드는 같은 패키지의 {@link Basecamp}만 부른다.
 */
@Entity
@Table(name = "basecamp_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BasecampMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "basecamp_id")
    private Basecamp basecamp;

    @Column(name = "member_id")
    private long memberId;

    @Enumerated(EnumType.STRING)
    private BasecampMemberRole role;

    @Enumerated(EnumType.STRING)
    private BasecampMemberStatus status;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "early_leave")
    private boolean earlyLeave;

    @Column(name = "kick_reason")
    @Enumerated(EnumType.STRING)
    private KickReason kickReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private BasecampMember(Basecamp basecamp, long memberId, BasecampMemberRole role, Instant now) {
        this.basecamp = basecamp;
        this.memberId = memberId;
        this.role = role;
        this.status = BasecampMemberStatus.ACTIVE;
        this.joinedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    static BasecampMember leader(Basecamp basecamp, long memberId, Instant now) {
        return new BasecampMember(basecamp, memberId, BasecampMemberRole.LEADER, now);
    }

    static BasecampMember member(Basecamp basecamp, long memberId, Instant now) {
        return new BasecampMember(basecamp, memberId, BasecampMemberRole.MEMBER, now);
    }

    public boolean isActive() {
        return status == BasecampMemberStatus.ACTIVE;
    }

    public boolean isLeader() {
        return role == BasecampMemberRole.LEADER;
    }

    void leave(boolean early, Instant now) {
        this.status = BasecampMemberStatus.LEFT;
        this.earlyLeave = early;
        this.leftAt = now;
        this.updatedAt = now;
    }

    // 제재를 받거나 회원 탈퇴 때문에 빠지는 멤버는 베이스캠프에서 스스로 나가는 것이 아니다. 그래서 출발이 임박했어도 임박 탈퇴로 세지 않는다.
    void removeByCleanup(Instant now) {
        this.status = BasecampMemberStatus.LEFT;
        this.earlyLeave = false;
        this.leftAt = now;
        this.updatedAt = now;
    }

    void kick(KickReason reason, Instant now) {
        this.status = BasecampMemberStatus.KICKED;
        this.kickReason = reason;
        this.leftAt = now;
        this.updatedAt = now;
    }
}
