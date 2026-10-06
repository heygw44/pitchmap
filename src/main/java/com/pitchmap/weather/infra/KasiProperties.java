package com.pitchmap.weather.infra;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 한국천문연구원 출몰시각 API 설정.
 *
 * @param baseUrl 출몰시각 API의 기본 주소. 서비스 경로(예: {@code /RiseSetInfoService/getLCRiseSetInfo})를 이 주소 뒤에 붙여 호출한다.
 * @param serviceKey 공공데이터포털 인증키(Decoding 키). 고캠핑, 기상청과 같은 키를 쓴다. 비밀값이라 환경 변수로만 받는다. 값이 없으면 앱은 뜨지만 API가 인증 오류를 돌려준다.
 * @param connectTimeout 서버와 연결을 맺을 때까지 기다리는 시간
 * @param readTimeout 요청을 보낸 뒤 응답을 받을 때까지 기다리는 시간. 장소 상세 응답이 출몰시각 때문에 오래 걸리지 않게 짧게 둔다.
 */
@ConfigurationProperties("pitchmap.weather.kasi")
public record KasiProperties(
        @DefaultValue("https://apis.data.go.kr/B090041/openapi/service")
        String baseUrl,

        String serviceKey,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("2s") Duration readTimeout) {

    private static final String PREFIX = "pitchmap.weather.kasi.";

    public KasiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "baseUrl must not be blank");
        }
        serviceKey = Objects.requireNonNullElse(serviceKey, "");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + name + " must be positive");
        }
    }

    /** record가 만드는 기본 toString은 인증키를 그대로 출력한다. 누군가 설정을 로그에 찍어도 키가 남지 않게 가린다. */
    @Override
    public String toString() {
        return "KasiProperties[baseUrl=" + baseUrl + ", serviceKey=****, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
