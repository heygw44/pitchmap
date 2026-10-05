package com.pitchmap.member.domain;

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

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    public static final int NICKNAME_MIN_LENGTH = 2;
    public static final int NICKNAME_MAX_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    private String nickname;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    @Column(name = "self_age_group")
    private String selfAgeGroup;

    @Column(name = "self_gender")
    private String selfGender;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "suspended_until")
    private Instant suspendedUntil;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private Member(Email email, String passwordHash, String nickname, Instant now) {
        this.email = email.value();
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.status = MemberStatus.UNVERIFIED;
        this.role = MemberRole.USER;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Member register(Email email, String passwordHash, String nickname, Instant now) {
        if (email == null || passwordHash == null || now == null) {
            throw new IllegalArgumentException("회원 생성에 필요한 값이 null입니다.");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시가 비어 있습니다.");
        }
        if (nickname == null
                || nickname.isBlank()
                || nickname.length() < NICKNAME_MIN_LENGTH
                || nickname.length() > NICKNAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "닉네임은 공백이 아닌 %d~%d자여야 합니다.".formatted(NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH));
        }
        return new Member(email, passwordHash, nickname, now);
    }

    /** 호출하면 비밀번호 해시와 수정 시각만 바꾼다. 회원 상태와 다른 값은 그대로 둔다. */
    public void changePassword(String passwordHash, Instant now) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시가 비어 있습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("비밀번호를 바꾼 시각이 null입니다.");
        }
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }
}
