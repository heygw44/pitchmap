package com.pitchmap.trust.infra;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 본인확인 설정.
 *
 * @param ciHmacKey CI를 해시할 때 쓰는 비밀 키. 비밀값이라 환경 변수로만 받는다. 키가 없으면 본인확인을 처리할 수 없고 짧은 키는 추측하기 쉬워서,
 *     비어 있거나 {@value #MIN_KEY_BYTES}바이트(UTF-8)보다 짧으면 앱이 시작하지 않는다. 키를 바꾸면 이미 저장한 CI 해시와 새 해시가 달라져 같은
 *     사람인지 알아볼 수 없으므로 운영 키는 바꾸지 않는다.
 */
@ConfigurationProperties("pitchmap.identity")
public record IdentityProperties(String ciHmacKey) {

    static final int MIN_KEY_BYTES = 32;

    private static final String PREFIX = "pitchmap.identity.";

    public IdentityProperties {
        if (ciHmacKey == null || ciHmacKey.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "ciHmacKey must not be blank (set CI_HMAC_KEY)");
        }
        if (ciHmacKey.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_BYTES) {
            throw new IllegalArgumentException(PREFIX + "ciHmacKey must be at least " + MIN_KEY_BYTES + " bytes");
        }
    }

    /** record가 만드는 기본 toString은 키를 그대로 출력한다. 누군가 설정을 로그에 찍어도 키가 남지 않게 가린다. */
    @Override
    public String toString() {
        return "IdentityProperties[ciHmacKey=****]";
    }
}
