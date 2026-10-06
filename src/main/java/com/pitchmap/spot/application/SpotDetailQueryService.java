package com.pitchmap.spot.application;

import com.pitchmap.weather.application.SunQueryService;
import com.pitchmap.weather.application.SunTimes;
import com.pitchmap.weather.application.WeatherForecast;
import com.pitchmap.weather.application.WeatherQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 장소 한 곳의 상세를 찾고 날씨를 붙인다.
 *
 * <p>외부 API(기상청, 천문연)를 DB 트랜잭션 밖에서 부르려고 이 서비스에는 트랜잭션을 두지 않는다. DB 조회는 {@link SpotDetailReader}가
 * 읽기 트랜잭션 안에서 하고, 끝난 뒤에 외부 API를 부른다.
 */
@Service
@RequiredArgsConstructor
public class SpotDetailQueryService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final SpotDetailReader spotDetailReader;
    private final WeatherQueryService weatherQueryService;
    private final SunQueryService sunQueryService;
    private final Clock clock;

    /**
     * 호출하면 spotId인 장소의 상세를 돌려준다.
     *
     * <p>장소가 없거나 숨김, 삭제, 검토 대기처럼 ACTIVE가 아니면 서비스는 NOT_FOUND로 거부한다. 기상청 호출이 실패하면 weather를 null로 두고,
     * 이때 천문연은 부르지 않는다. 기상청이 성공하고 천문연만 실패하면 weather는 채우고 sun만 null로 둔다. 출몰시각의 날짜는 오늘 한국 날짜다.
     */
    public SpotDetail findDetail(long spotId) {
        SpotDetailReader.Read read = spotDetailReader.read(spotId);
        SpotDetail detail = read.detail();
        Optional<WeatherForecast> forecast =
                weatherQueryService.forecast(read.weatherNx(), read.weatherNy(), detail.lat(), detail.lng());
        if (forecast.isEmpty()) {
            return detail;
        }
        LocalDate today = LocalDate.now(clock.withZone(KOREA));
        SunTimes sun =
                sunQueryService.sunTimes(detail.lat(), detail.lng(), today).orElse(null);
        return detail.withWeather(new SpotWeather(forecast.get(), sun));
    }
}
