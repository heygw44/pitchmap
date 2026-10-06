package com.pitchmap.trust.infra;

import com.pitchmap.trust.domain.CiHash;
import com.pitchmap.trust.domain.CiHasher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 비밀 키로 CI를 HMAC-SHA256 해시한다. 키가 없으면 CI 해시를 만들 수 없으므로 키는 설정에서 받는다.
 *
 * <p>{@link Mac}은 스레드에 안전하지 않다. 그래서 호출할 때마다 새로 만든다.
 */
@Component
class HmacCiHasher implements CiHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    HmacCiHasher(IdentityProperties properties) {
        this.key = new SecretKeySpec(properties.ciHmacKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    @Override
    public CiHash hash(String ci) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return new CiHash(HexFormat.of().formatHex(mac.doFinal(ci.getBytes(StandardCharsets.UTF_8))));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("CI 해시를 만들 수 없습니다.", e);
        }
    }
}
