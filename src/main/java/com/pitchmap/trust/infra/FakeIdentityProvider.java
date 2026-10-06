package com.pitchmap.trust.infra;

import com.pitchmap.trust.domain.IdentityClaim;
import com.pitchmap.trust.domain.IdentityProvider;
import com.pitchmap.trust.domain.IdentityProviderType;
import com.pitchmap.trust.domain.VerifiedIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 실제 본인확인기관과 계약할 수 없어서 시연에 쓰는 가짜 제공자다.
 *
 * <p>회원이 낸 출생연도와 성별을 그대로 믿는다. CI는 시연용 식별 문자열에서 만든다. 같은 문자열이면 항상 같은 CI가 나오므로, 서로 다른 계정이
 * 같은 사람인 경우(한 사람이 계정을 두 개 만드는 경우)를 시연할 수 있다.
 */
@Component
class FakeIdentityProvider implements IdentityProvider {

    private static final String CI_SEED_PREFIX = "pitchmap-fake-ci:";

    @Override
    public VerifiedIdentity verify(IdentityClaim claim) {
        return new VerifiedIdentity(deriveCi(claim.demoIdentityKey()), claim.birthYear(), claim.gender());
    }

    @Override
    public IdentityProviderType type() {
        return IdentityProviderType.FAKE;
    }

    private static String deriveCi(String demoIdentityKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((CI_SEED_PREFIX + demoIdentityKey).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM이 SHA-256을 지원하지 않습니다.", e);
        }
    }
}
