package com.pitchmap.publicdata.infra;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 한국관광공사 고캠핑 API 설정.
 *
 * @param baseUrl 고캠핑 API의 기본 주소. 작업 이름(예: {@code /basedSyncList})을 이 주소 뒤에 붙여 호출한다.
 * @param serviceKey 공공데이터포털 인증키(Decoding 키). 비밀값이라 환경 변수로만 받는다. 값이 없으면 앱은 뜨지만 API가 인증 오류를 돌려준다.
 * @param connectTimeout 서버와 연결을 맺을 때까지 기다리는 시간
 * @param readTimeout 요청을 보낸 뒤 응답을 받을 때까지 기다리는 시간
 * @param pageSize 동기화할 때 한 번에 요청하는 캠핑장 수
 */
@ConfigurationProperties("pitchmap.publicdata.gocamping")
public record GoCampingProperties(
        @DefaultValue("https://apis.data.go.kr/B551011/GoCamping")
        String baseUrl,

        String serviceKey,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("100") int pageSize) {

    private static final String PREFIX = "pitchmap.publicdata.gocamping.";

    public GoCampingProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "baseUrl must not be blank");
        }
        serviceKey = Objects.requireNonNullElse(serviceKey, "");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        if (pageSize <= 0) {
            throw new IllegalArgumentException(PREFIX + "pageSize must be positive");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + name + " must be positive");
        }
    }

    /** record가 만드는 기본 toString은 인증키를 그대로 출력한다. 누군가 설정을 로그에 찍어도 키가 남지 않게 가린다. */
    @Override
    public String toString() {
        return "GoCampingProperties[baseUrl=" + baseUrl + ", serviceKey=****, connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", pageSize=" + pageSize + "]";
    }
}
