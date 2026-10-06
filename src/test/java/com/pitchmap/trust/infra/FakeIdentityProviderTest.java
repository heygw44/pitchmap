package com.pitchmap.trust.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.trust.domain.Gender;
import com.pitchmap.trust.domain.IdentityClaim;
import com.pitchmap.trust.domain.IdentityProviderType;
import com.pitchmap.trust.domain.VerifiedIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FakeIdentityProviderTest {

    private final FakeIdentityProvider provider = new FakeIdentityProvider();

    @Test
    @DisplayName("[ID-04] 같은 시연용 식별 문자열이면 출생연도·성별이 달라도 같은 CI를 만든다")
    void sameKeyGivesSameCi() {
        VerifiedIdentity first = provider.verify(new IdentityClaim(1995, Gender.FEMALE, "demo-1"));
        VerifiedIdentity second = provider.verify(new IdentityClaim(1990, Gender.MALE, "demo-1"));

        assertThat(first.ci()).isEqualTo(second.ci());
    }

    @Test
    @DisplayName("다른 시연용 식별 문자열이면 다른 CI를 만들고, 출생연도·성별은 입력 그대로 돌려준다")
    void differentKeyGivesDifferentCi() {
        VerifiedIdentity first = provider.verify(new IdentityClaim(1995, Gender.FEMALE, "demo-1"));
        VerifiedIdentity second = provider.verify(new IdentityClaim(1995, Gender.FEMALE, "demo-2"));

        assertThat(first.ci()).isNotEqualTo(second.ci());
        assertThat(first.birthYear()).isEqualTo(1995);
        assertThat(first.gender()).isEqualTo(Gender.FEMALE);
        assertThat(provider.type()).isEqualTo(IdentityProviderType.FAKE);
    }

    @Test
    @DisplayName("[ID-01] 신원과 요청 값의 toString에 CI·출생연도·식별 문자열이 나오지 않는다")
    void toStringMasksSensitiveValues() {
        IdentityClaim claim = new IdentityClaim(1995, Gender.FEMALE, "demo-secret-key");
        VerifiedIdentity identity = provider.verify(claim);

        assertThat(claim.toString()).doesNotContain("demo-secret-key").doesNotContain("1995");
        assertThat(identity.toString()).doesNotContain(identity.ci()).doesNotContain("1995");
    }
}
