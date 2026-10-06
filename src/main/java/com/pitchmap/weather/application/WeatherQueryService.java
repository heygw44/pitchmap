package com.pitchmap.weather.application;

import com.pitchmap.common.external.ExternalApiMetrics;
import com.pitchmap.weather.application.WeatherForecast.MidTermForecast;
import com.pitchmap.weather.application.WeatherForecast.ShortTermForecast;
import com.pitchmap.weather.domain.KmaBaseTimes;
import com.pitchmap.weather.domain.MidTermRegion;
import com.pitchmap.weather.domain.MidTermRegions;
import com.pitchmap.weather.infra.KmaApiException;
import com.pitchmap.weather.infra.KmaClient;
import com.pitchmap.weather.infra.KmaMidTermItem;
import com.pitchmap.weather.infra.KmaShortTermItem;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 장소의 날씨 예보를 기상청에서 조회한다.
 *
 * <p>외부 API 호출을 DB 트랜잭션 안에서 하지 않으려고 이 서비스에는 트랜잭션이 없다. 기상청이 실패하거나 응답을 해석하지 못하면 예외를 던지지
 * 않고 빈 값을 돌려준다. 그래서 장소 상세는 날씨 없이도 정상으로 응답한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherQueryService {

    private static final String API = "kma";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final KmaClient kmaClient;
    private final MidTermRegions midTermRegions;
    private final ExternalApiMetrics metrics;
    private final Clock clock;

    /**
     * 장소의 단기·중기 예보를 조회한다. 단기, 중기 육상, 중기 기온 순서로 부르고, 하나라도 실패하면 남은 호출을 하지 않고 빈 값을 돌려준다.
     * 일부 값만 있는 날씨를 보여 주면 사용자가 빠진 값을 "비 안 옴" 같은 뜻으로 읽을 수 있기 때문이다.
     *
     * @param nx 장소의 기상청 격자 X
     * @param ny 장소의 기상청 격자 Y
     * @param lat 장소의 위도. 중기예보 구역을 고를 때 쓴다.
     * @param lng 장소의 경도
     * @return 예보. 기상청 호출이 실패했거나 응답을 해석하지 못했으면 빈 값
     */
    public Optional<WeatherForecast> forecast(int nx, int ny, double lat, double lng) {
        try {
            return Optional.of(fetchForecast(nx, ny, lat, lng));
        } catch (KmaApiException e) {
            log.warn("기상청 날씨를 받지 못해 날씨 없이 응답한다. reason={}", e.getMessage());
            metrics.recordFallback(API);
            return Optional.empty();
        }
    }

    private WeatherForecast fetchForecast(int nx, int ny, double lat, double lng) {
        Instant now = clock.instant();
        List<KmaShortTermItem> shortTermItems = kmaClient.getVilageFcst(KmaBaseTimes.shortTermBase(now), nx, ny);
        List<ShortTermForecast> shortTerm = KmaForecastConverter.toShortTerm(shortTermItems, now);

        MidTermRegion region = midTermRegions.nearest(lat, lng);
        LocalDateTime tmFc = KmaBaseTimes.midTermBase(now);
        Optional<KmaMidTermItem> land = kmaClient.getMidLandFcst(region.landRegId(), tmFc);
        Optional<KmaMidTermItem> ta = kmaClient.getMidTa(region.taRegId(), tmFc);
        List<MidTermForecast> midTerm =
                KmaForecastConverter.toMidTerm(land, ta, tmFc.toLocalDate(), LocalDate.ofInstant(now, KST));

        return new WeatherForecast(WeatherForecast.KMA_SOURCE, shortTerm, midTerm);
    }
}
