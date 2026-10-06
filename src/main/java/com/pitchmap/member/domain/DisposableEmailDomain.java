package com.pitchmap.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "disposable_email_domain")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisposableEmailDomain {

    @Id
    private String domain;

    @Enumerated(EnumType.STRING)
    private DisposableEmailDomainSource source;

    @Column(name = "created_at")
    private Instant createdAt;

    private DisposableEmailDomain(String domain, DisposableEmailDomainSource source, Instant createdAt) {
        this.domain = domain;
        this.source = source;
        this.createdAt = createdAt;
    }

    public static DisposableEmailDomain of(String domain, DisposableEmailDomainSource source, Instant now) {
        if (domain == null || domain.isBlank() || source == null || now == null) {
            throw new IllegalArgumentException("차단 도메인 생성에 필요한 값이 올바르지 않습니다.");
        }
        return new DisposableEmailDomain(domain.toLowerCase(Locale.ROOT), source, now);
    }
}
