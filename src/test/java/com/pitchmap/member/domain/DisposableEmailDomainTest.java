package com.pitchmap.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisposableEmailDomainTest {

    @Test
    @DisplayName("[F-01][ID-01] 도메인을 소문자로 저장하고 출처와 생성 시각을 유지한다")
    void of_lowercasesDomain() {
        DisposableEmailDomain domain = DisposableEmailDomain.of(
                "Mailinator.COM", DisposableEmailDomainSource.ADMIN, MutableClock.DEFAULT_INSTANT);

        assertThat(domain.getDomain()).isEqualTo("mailinator.com");
        assertThat(domain.getSource()).isEqualTo(DisposableEmailDomainSource.ADMIN);
        assertThat(domain.getCreatedAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }
}
