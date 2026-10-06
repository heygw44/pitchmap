package com.pitchmap.weather.application;

import com.pitchmap.common.external.ExternalApiMetrics;
import com.pitchmap.weather.infra.KasiApiException;
import com.pitchmap.weather.infra.KasiClient;
import com.pitchmap.weather.infra.KasiRiseSetItem;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 장소의 일출·일몰·시민박명 시각을 천문연에서 조회한다.
 *
 * <p>외부 API 호출을 DB 트랜잭션 안에서 하지 않으려고 이 서비스에는 트랜잭션이 없다. 천문연이 실패하거나 응답을 해석하지 못하면 예외를
 * 던지지 않고 빈 값을 돌려준다. 그래서 장소 상세는 출몰시각 없이도 정상으로 응답한다. 기상청과 천문연은 따로 실패할 수 있어서, 날씨
 * 서비스({@link WeatherQueryService})와 나눠 두었다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SunQueryService {

    private static final String API = "kasi";

    private final KasiClient kasiClient;
    private final ExternalApiMetrics metrics;

    /**
     * 위치와 날짜의 출몰시각을 조회한다. 날짜는 호출하는 쪽이 정한다.
     *
     * @param lat 장소의 위도
     * @param lng 장소의 경도
     * @param date 조회할 날짜(한국 날짜)
     * @return 출몰시각. 천문연 호출이 실패했거나, 항목이 없거나, 값을 해석하지 못했으면 빈 값
     */
    public Optional<SunTimes> sunTimes(double lat, double lng, LocalDate date) {
        try {
            Optional<KasiRiseSetItem> item = kasiClient.getRiseSet(date, lat, lng);
            if (item.isEmpty()) {
                return fallback("천문연이 출몰시각 항목을 주지 않았다");
            }
            return Optional.of(KasiSunTimesConverter.toSunTimes(item.get()));
        } catch (KasiApiException e) {
            return fallback(e.getMessage());
        }
    }

    // 좌표는 위치 정보라서 로그에 남기지 않고 사유만 남긴다.
    private Optional<SunTimes> fallback(String reason) {
        log.warn("천문연 출몰시각을 받지 못해 출몰시각 없이 응답한다. reason={}", reason);
        metrics.recordFallback(API);
        return Optional.empty();
    }
}
