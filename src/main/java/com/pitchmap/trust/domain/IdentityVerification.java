package com.pitchmap.trust.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 본인확인을 마친 기록. 회원당 하나다.
 *
 * <p>CI 원값은 저장하지 않고 해시({@link CiHash})만 저장한다. 회원은 다른 모듈의 엔티티이므로 연관관계로 걸지 않고 ID로만 가리킨다.
 */
@Entity
@Table(name = "identity_verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityVerification {

    /** 만 19세가 되는 해의 1월 1일부터 성인이다. 출생연도에 이 값을 더한 해가 성인이 되는 해다. */
    public static final int ADULT_AGE = 19;

    /** 탈퇴한 회원의 CI 해시를 남겨 두는 기본 기간이다. 같은 사람이 탈퇴 직후 새 계정으로 본인확인하는 것을 막는다. */
    public static final Duration CI_RETENTION = Duration.ofDays(30);

    /** 해제되지 않은 제재 이력이 있는 회원이 탈퇴하면 CI 해시를 남겨 두는 기간이다. 제재를 피하려고 탈퇴하고 다시 가입하는 것을 막는다. */
    public static final Duration CI_RETENTION_WITH_SANCTION = Duration.ofDays(365);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    // 탈퇴하면 NULL이 되므로 래퍼 타입으로 둔다.
    @Column(name = "birth_year")
    private Short birthYear;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Convert(converter = CiHashConverter.class)
    @Column(name = "ci_hash")
    private CiHash ciHash;

    @Enumerated(EnumType.STRING)
    private IdentityProviderType provider;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "ci_retained_until")
    private Instant ciRetainedUntil;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private IdentityVerification(
            long memberId, VerifiedIdentity identity, CiHash ciHash, IdentityProviderType provider, Instant now) {
        this.memberId = memberId;
        this.birthYear = (short) identity.birthYear();
        this.gender = identity.gender();
        this.ciHash = ciHash;
        this.provider = provider;
        this.verifiedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 호출하면 memberId인 회원이 identity로 본인확인을 마친 기록을 만든다. CI 원값은 담지 않고 ciHash만 담는다.
     *
     * <p>인자가 null이거나 출생연도·성별이 비어 있으면 {@link IllegalArgumentException}을 던진다. 출생연도가 올해를 넘는지는 시각을 아는 호출하는 쪽이
     * 검사한다.
     */
    public static IdentityVerification verify(
            long memberId, VerifiedIdentity identity, CiHash ciHash, IdentityProviderType provider, Instant now) {
        if (identity == null || ciHash == null || provider == null || now == null) {
            throw new IllegalArgumentException("본인확인 기록을 만드는 데 필요한 값이 null입니다.");
        }
        if (identity.gender() == null) {
            throw new IllegalArgumentException("본인확인 기록에는 성별이 필요합니다.");
        }
        if (identity.birthYear() < Short.MIN_VALUE || identity.birthYear() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("출생연도가 범위를 벗어났습니다.");
        }
        return new IdentityVerification(memberId, identity, ciHash, provider, now);
    }

    /** 호출하면 탈퇴 시각 withdrawnAt에 보관 기간을 더한 CI 해시 보관 기한을 돌려준다. 제재 이력이 있으면 더 긴 기간을 쓴다. */
    public static Instant ciRetainedUntil(Instant withdrawnAt, boolean hasSanctionHistory) {
        return withdrawnAt.plus(hasSanctionHistory ? CI_RETENTION_WITH_SANCTION : CI_RETENTION);
    }

    /**
     * 호출하면 탈퇴한 회원의 출생연도와 성별을 지우고 CI 해시 보관 기한을 기록한다. CI 해시는 기한이 끝날 때까지 남겨
     * 같은 사람의 중복 본인확인을 막는다. 이미 기한이 있으면 그 값을 새 기한으로 바꾼다.
     */
    public void withdraw(Instant retainedUntil, Instant now) {
        if (retainedUntil == null || now == null) {
            throw new IllegalArgumentException("보관 기한이나 수정 시각이 null입니다.");
        }
        this.birthYear = null;
        this.gender = null;
        this.ciRetainedUntil = retainedUntil;
        this.updatedAt = now;
    }

    /** currentYear가 속한 해에 성인이면 true다. 출생연도가 없으면(탈퇴 뒤) 성인으로 보지 않는다. */
    public boolean isAdult(Year currentYear) {
        return birthYear != null && birthYear + ADULT_AGE <= currentYear.getValue();
    }

    /** currentYear 기준 연 나이(올해 − 출생연도)로 계산한 연령대다. 출생연도가 없거나 성인이 아니면 빈 값이다. */
    public Optional<AgeGroup> ageGroupIn(Year currentYear) {
        if (birthYear == null) {
            return Optional.empty();
        }
        return AgeGroup.fromAge(currentYear.getValue() - birthYear);
    }
}
