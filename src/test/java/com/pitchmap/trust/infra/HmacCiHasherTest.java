package com.pitchmap.trust.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HmacCiHasherTest {

    private static final String KEY = "unit-test-only-hmac-key-0123456789-abcdef";
    private static final String OTHER_KEY = "other-unit-test-only-hmac-key-9876543210-fedcba";

    @Test
    @DisplayName("[ID-02] 같은 키와 같은 CI는 항상 같은 해시이고, 소문자 16진수 64자다")
    void sameInputGivesSameHash() {
        HmacCiHasher hasher = new HmacCiHasher(new IdentityProperties(KEY));

        assertThat(hasher.hash("ci-1")).isEqualTo(hasher.hash("ci-1"));
        assertThat(hasher.hash("ci-1").value()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("[ID-02] CI가 다르거나 키가 다르면 해시가 다르고, 해시에 CI 원값이 들어 있지 않다")
    void differentInputOrKeyGivesDifferentHash() {
        HmacCiHasher hasher = new HmacCiHasher(new IdentityProperties(KEY));
        HmacCiHasher otherKeyHasher = new HmacCiHasher(new IdentityProperties(OTHER_KEY));

        assertThat(hasher.hash("ci-1")).isNotEqualTo(hasher.hash("ci-2"));
        assertThat(hasher.hash("ci-1")).isNotEqualTo(otherKeyHasher.hash("ci-1"));
        assertThat(hasher.hash("ci-1").value()).doesNotContain("ci-1");
    }
}
