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
import java.util.Set;
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
    public static final int EMAIL_MAX_LENGTH = 254;

    // 글자(Lo) 범주라서 범주 검사로는 걸러지지 않지만 화면에는 아무것도 그리지 않는 한글 채움 문자다.
    private static final Set<Integer> BLANK_LETTERS = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0);

    public static final String NICKNAME_RULE_MESSAGE =
            "닉네임은 " + NICKNAME_MIN_LENGTH + "~" + NICKNAME_MAX_LENGTH + "자여야 합니다. 앞뒤 공백, 연속 공백, 보이지 않는 문자는 쓸 수 없습니다.";

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
     * 미인증 일반 회원을 만든다. 닉네임이 규칙을 어기면 닉네임을 바꿀 때와 같은 입력 오류 예외를 던진다.
     * 이메일, 비밀번호 해시, 시각이 비어 있으면 호출하는 쪽의 버그라서 {@link IllegalArgumentException}을 던진다.
     */
    public static Member register(Email email, String passwordHash, String nickname, Instant now) {
        if (email == null || passwordHash == null || now == null) {
            throw new IllegalArgumentException("회원 생성에 필요한 값이 null입니다.");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시가 비어 있습니다.");
        }
        requireValidNickname(nickname);
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
     * 호출하면 회원을 until까지 정지 상태로 바꾼다. 탈퇴한 회원은 그대로 둔다.
     * 종료 시각이 없는 영구 정지와, 이미 until보다 늦게 끝나는 정지도 그대로 둔다. 그래서 짧은 임시 정지가 긴 정지를 줄이지 못한다.
     */
    public void suspendTemporarily(Instant until, Instant now) {
        if (until == null || now == null) {
            throw new IllegalArgumentException("정지 종료 시각이나 수정 시각이 null입니다.");
        }
        if (status == MemberStatus.WITHDRAWN) {
            return;
        }
        if (status == MemberStatus.SUSPENDED && (suspendedUntil == null || suspendedUntil.isAfter(until))) {
            return;
        }
        this.status = MemberStatus.SUSPENDED;
        this.suspendedUntil = until;
        this.updatedAt = now;
    }

    /**
     * 호출하면 회원을 종료 시각 없이 정지한다(영구 정지). 이미 정지 중이면 종료 시각을 지운다. 탈퇴한 회원은 그대로 둔다.
     */
    public void suspendPermanently(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("수정 시각이 null입니다.");
        }
        if (status == MemberStatus.WITHDRAWN) {
            return;
        }
        this.status = MemberStatus.SUSPENDED;
        this.suspendedUntil = null;
        this.updatedAt = now;
    }

    /**
     * 호출하면 정지 종료 시각이 now와 같거나 지났을 때 정지를 풀고 true를 돌려준다. 이메일 인증을 마친 회원은 ACTIVE로, 마치지 못한 회원은
     * UNVERIFIED로 돌아간다. 정지 중이 아니거나, 종료 시각이 없는 영구 정지이거나, 아직 끝나지 않았으면 아무것도 바꾸지 않고 false를 돌려준다.
     */
    public boolean releaseSuspensionIfExpired(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("현재 시각이 null입니다.");
        }
        if (status != MemberStatus.SUSPENDED || suspendedUntil == null || suspendedUntil.isAfter(now)) {
            return false;
        }
        this.status = emailVerifiedAt != null ? MemberStatus.ACTIVE : MemberStatus.UNVERIFIED;
        this.suspendedUntil = null;
        this.updatedAt = now;
        return true;
    }

    /**
     * 호출하면 닉네임과 수정 시각만 바꾼다. 닉네임이 규칙을 어기면 입력 오류 예외를 던지고 아무것도 바꾸지 않는다.
     */
    public void changeNickname(String nickname, Instant now) {
        requireValidNickname(nickname);
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

    /**
     * 호출하면 닉네임이 규칙을 지키는지 알려 준다. 길이는 실제 글자(코드포인트) 수로 센다. 이모지처럼 UTF-16 문자 두 개로
     * 이뤄진 글자도 DB 컬럼 길이 기준과 맞추려고 한 글자로 센다.
     *
     * <p>화면에서 같아 보이는 닉네임을 만들 수 있는 글자는 받지 않는다. DB 유니크 제약은 NBSP와 한글 채움 문자를 다른 글자로 보고,
     * 폭 없는 공백은 아예 무시한다. 그래서 그대로 두면 {@code hiker}에 NBSP를 붙인 닉네임이 {@code hiker}를 사칭하며 통과한다.
     * 이를 막으려고 일반 공백(U+0020)은 닉네임 가운데에 한 칸만 허용하고, 그 밖의 공백류·제어·서식 문자와 한글 채움 문자는 거부한다.
     * ZWJ(U+200D)도 서식 문자라서 ZWJ로 이은 이모지는 쓸 수 없다.
     */
    public static boolean isValidNickname(String nickname) {
        if (nickname == null) {
            return false;
        }
        int length = nickname.codePointCount(0, nickname.length());
        if (length < NICKNAME_MIN_LENGTH || length > NICKNAME_MAX_LENGTH) {
            return false;
        }
        boolean misplacedSpace = nickname.startsWith(" ") || nickname.endsWith(" ") || nickname.contains("  ");
        return !misplacedSpace && nickname.codePoints().noneMatch(Member::isHiddenOrSpaceLike);
    }

    // 닉네임 가운데의 일반 공백은 위치와 개수를 따로 검사하므로 여기서는 보이지 않는 글자가 아닌 것으로 본다.
    private static boolean isHiddenOrSpaceLike(int codePoint) {
        if (codePoint == ' ') {
            return false;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.SPACE_SEPARATOR,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR -> true;
            default -> BLANK_LETTERS.contains(codePoint);
        };
    }

    /**
     * 호출하면 닉네임이 규칙을 어길 때 입력 오류 예외를 던진다. 서비스는 중복 조회보다 먼저 이 메서드를 불러야 한다.
     * 중복 조회는 DB 정렬 규칙으로 비교하는데, 이 규칙은 폭 없는 공백을 무시한다. 그래서 검증하지 않으면 폭 없는 공백이 붙은 닉네임이
     * 입력 오류가 아니라 닉네임 중복으로 보인다.
     */
    public static void requireValidNickname(String nickname) {
        if (!isValidNickname(nickname)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, NICKNAME_RULE_MESSAGE);
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("수정 시각이 null입니다.");
        }
    }
}
