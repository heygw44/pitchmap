package com.pitchmap.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 혼합 영속성 테스트 전용 엔티티. 운영 테이블 하나를 JPA 쪽에서 다룬다. */
@Entity
@Table(name = "disposable_email_domain")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainProbe {

    @Id
    private String domain;

    private String source;

    @Column(name = "created_at")
    private Instant createdAt;

    public DomainProbe(String domain, String source, Instant createdAt) {
        this.domain = domain;
        this.source = source;
        this.createdAt = createdAt;
    }
}
