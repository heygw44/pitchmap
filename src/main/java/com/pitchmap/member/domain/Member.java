package com.pitchmap.member.domain;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
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

    private static final String NICKNAME_RULE_MESSAGE =
            "닉네임은 공백이 아닌 %d~%d자여야 합니다.".formatted(NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    private String nickname;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_age_group")
    private SelfAgeGroup selfAgeGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "self_gender")
    private SelfGender selfGender;

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

    /**
     * 미인증 일반 회원을 만든다. 닉네임이 공백이거나 길이가 범위를 벗어나면 닉네임을 바꿀 때와 같은 입력 오류 예외를 던진다.
     * 이메일, 비밀번호 해시, 시각이 비어 있으면 호출하는 쪽의 버그라서 {@link IllegalArgumentException}을 던진다.
     */
    public static Member register(Email email, String passwordHash, String nickname, Instant now) {
        if (email == null || passwordHash == null || now == null) {
            throw new IllegalArgumentException("회원 생성에 필요한 값이 null입니다.");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시가 비어 있습니다.");
        }
        validateNickname(nickname);
        return new Member(email, passwordHash, nickname, now);
    }

    /** 호출하면 비밀번호 해시, 비밀번호를 바꾼 시각, 수정 시각만 바꾼다. 회원 상태와 다른 값은 그대로 둔다. */
    public void changePassword(String passwordHash, Instant now) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시가 비어 있습니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("비밀번호를 바꾼 시각이 null입니다.");
        }
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.updatedAt = now;
    }

    /**
     * 호출하면 닉네임과 수정 시각만 바꾼다. 닉네임이 공백이거나 길이가 범위를 벗어나면 입력 오류 예외를 던지고
     * 아무것도 바꾸지 않는다.
     */
    public void changeNickname(String nickname, Instant now) {
        validateNickname(nickname);
        requireNow(now);
        this.nickname = nickname;
        this.updatedAt = now;
    }

    /** 호출하면 스스로 밝힌 연령대와 수정 시각을 바꾼다. null을 넘기면 연령대를 지운다. */
    public void changeSelfAgeGroup(SelfAgeGroup selfAgeGroup, Instant now) {
        requireNow(now);
        this.selfAgeGroup = selfAgeGroup;
        this.updatedAt = now;
    }

    /** 호출하면 스스로 밝힌 성별과 수정 시각을 바꾼다. null을 넘기면 성별을 지운다. */
    public void changeSelfGender(SelfGender selfGender, Instant now) {
        requireNow(now);
        this.selfGender = selfGender;
        this.updatedAt = now;
    }

    // 닉네임은 회원이 직접 입력하는 값이라, 가입과 닉네임 변경 모두 규칙을 어기면 같은 입력 오류 예외를 던진다.
    private static void validateNickname(String nickname) {
        if (nickname == null
                || nickname.isBlank()
                || nickname.length() < NICKNAME_MIN_LENGTH
                || nickname.length() > NICKNAME_MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, NICKNAME_RULE_MESSAGE);
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("수정 시각이 null입니다.");
        }
    }
}
