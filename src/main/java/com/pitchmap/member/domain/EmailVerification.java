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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 인증 코드를 발송한 기록 한 건. 발송할 때마다 행이 하나 생기고, 코드 원값은 저장하지 않고 해시만 저장한다.
 * 시도 횟수와 인증 시각은 동시 요청에서도 한 번만 바뀌어야 해서 MyBatis 조건부 UPDATE로 바꾸므로, 이 엔티티에는 변경 메서드가 없다.
 */
@Entity
@Table(name = "email_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    private static final int CODE_HASH_LENGTH = 64;
    private static final int REQUEST_IP_MAX_LENGTH = 45;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "code_hash")
    private String codeHash;

    @Column(name = "request_ip")
    private String requestIp;

    // 컬럼이 TINYINT라서 Hibernate의 스키마 검증을 통과하려면 JDBC 타입을 맞춰야 한다.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "attempt_count")
    private int attemptCount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private EmailVerification(long memberId, String codeHash, String requestIp, Instant now) {
        this.memberId = memberId;
        this.codeHash = codeHash;
        this.requestIp = requestIp;
        this.attemptCount = 0;
        this.expiresAt = now.plus(EmailVerificationPolicy.CODE_VALIDITY);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** @param codeHash {@link VerificationCode#hash(long, String)}의 결과. 코드 원값을 넘기면 안 된다. */
    public static EmailVerification issue(long memberId, String codeHash, String requestIp, Instant now) {
        if (codeHash == null || codeHash.length() != CODE_HASH_LENGTH) {
            throw new IllegalArgumentException("인증 코드 해시는 " + CODE_HASH_LENGTH + "자여야 합니다.");
        }
        if (requestIp == null || requestIp.isBlank() || requestIp.length() > REQUEST_IP_MAX_LENGTH) {
            throw new IllegalArgumentException("요청 IP는 1~" + REQUEST_IP_MAX_LENGTH + "자여야 합니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("코드를 발급한 시각이 null입니다.");
        }
        return new EmailVerification(memberId, codeHash, requestIp, now);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    /** 호출하면 유효 시간이 끝났는지 알려 준다. 만료 시각 정각부터 만료다. */
    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean hasReachedAttemptLimit() {
        return attemptCount >= EmailVerificationPolicy.MAX_ATTEMPTS;
    }

    public boolean matches(String code) {
        return VerificationCode.matches(memberId, code, codeHash);
    }

    @Override
    public String toString() {
        return "EmailVerification[id=" + id + ", memberId=" + memberId + "]";
    }
}
