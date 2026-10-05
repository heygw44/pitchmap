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

/**
 * 비밀번호 재설정 링크 한 건. 토큰 원값은 저장하지 않고 해시만 저장한다.
 * 사용 시각은 같은 링크를 동시에 쓰는 요청 중 하나만 성공해야 해서 MyBatis 조건부 UPDATE로 바꾸므로, 이 엔티티에는 변경 메서드가 없다.
 */
@Entity
@Table(name = "password_reset_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    private static final int TOKEN_HASH_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private PasswordResetToken(long memberId, String tokenHash, Instant now) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = now.plus(PasswordResetPolicy.TOKEN_VALIDITY);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** @param tokenHash {@link ResetToken#hash(String)}의 결과. 토큰 원값을 넘기면 안 된다. */
    public static PasswordResetToken issue(long memberId, String tokenHash, Instant now) {
        if (tokenHash == null || tokenHash.length() != TOKEN_HASH_LENGTH) {
            throw new IllegalArgumentException("재설정 토큰 해시는 " + TOKEN_HASH_LENGTH + "자여야 합니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("토큰을 발급한 시각이 null입니다.");
        }
        return new PasswordResetToken(memberId, tokenHash, now);
    }

    /** 호출하면 아직 쓰지 않았고 만료 전인지 알려 준다. 만료 시각 정각부터 만료다. */
    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "PasswordResetToken[id=" + id + ", memberId=" + memberId + "]";
    }
}
