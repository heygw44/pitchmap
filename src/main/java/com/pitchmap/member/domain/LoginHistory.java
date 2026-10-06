package com.pitchmap.member.domain;

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

/** 로그인 시도 한 건의 기록. 추가만 하고 고치지 않는다. */
@Entity
@Table(name = "login_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "attempted_email")
    private String attemptedEmail;

    private String ip;

    private boolean success;

    @Column(name = "created_at")
    private Instant createdAt;

    private LoginHistory(Long memberId, String attemptedEmail, String ip, boolean success, Instant now) {
        this.memberId = memberId;
        this.attemptedEmail = attemptedEmail;
        this.ip = ip;
        this.success = success;
        this.createdAt = now;
    }

    /** @param memberId 이메일에 해당하는 회원이 없으면 null이다. */
    public static LoginHistory failure(Long memberId, String attemptedEmail, String ip, Instant now) {
        return create(memberId, attemptedEmail, ip, false, now);
    }

    public static LoginHistory success(long memberId, String attemptedEmail, String ip, Instant now) {
        return create(memberId, attemptedEmail, ip, true, now);
    }

    private static LoginHistory create(Long memberId, String attemptedEmail, String ip, boolean success, Instant now) {
        if (attemptedEmail == null || ip == null || now == null) {
            throw new IllegalArgumentException("로그인 기록에 필요한 값이 null입니다.");
        }
        return new LoginHistory(memberId, attemptedEmail, ip, success, now);
    }
}
