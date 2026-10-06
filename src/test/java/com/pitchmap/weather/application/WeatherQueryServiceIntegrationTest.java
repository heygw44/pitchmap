package com.pitchmap.weather.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.weather.application.WeatherForecast.MidTermForecast;
import com.pitchmap.weather.application.WeatherForecast.ShortTermForecast;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

@IntegrationTest
class WeatherQueryServiceIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String FALLBACK_COUNTER = "pitchmap.external.api.fallbacks";
    // 서울시청, 기상청 격자 60·127
    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;

    @Autowired
    private WeatherQueryService service;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MutableClock clock;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        // 한국 시각 2026-10-04 12:58. 픽스처를 받은 시각이다. 단기 기준은 11시, 중기 기준은 06시 발표다.
        clock.setInstant(Instant.parse("2026-10-04T03:58:00Z"));
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청 응답이 정상이면 단기·중기 예보를 채우고 출처를 기상청으로 표시한다")
    void returnsShortAndMidTermForecast() {
        // given
        stubAllSuccess();

        // when
        Optional<WeatherForecast> result = service.forecast(60, 127, SEOUL_LAT, SEOUL_LNG);

        // then
        assertThat(result).isPresent();
        WeatherForecast forecast = result.get();
        assertThat(forecast.source()).isEqualTo("기상청");
        assertThat(forecast.shortTerm()).isNotEmpty();
        assertThat(forecast.shortTerm())
                .extracting(ShortTermForecast::at)
                .allMatch(at -> !at.isBefore(clock.instant()));
        assertThat(forecast.midTerm())
                .extracting(MidTermForecast::date, MidTermForecast::minTemperature, MidTermForecast::amSky)
                .first()
                .isEqualTo(tuple(LocalDate.of(2026, 10, 8), 10, "맑음"));
        assertThat(forecast.midTerm()).hasSize(7);
        wireMock.verify(
                getRequestedFor(urlPathEqualTo(MID_LAND_FCST_PATH)).withQueryParam("regId", equalTo("11B00000")));
        wireMock.verify(getRequestedFor(urlPathEqualTo(MID_TA_PATH)).withQueryParam("regId", equalTo("11B10101")));
    }

    @Test
    @DisplayName("[F-09][NFR-05] 단기예보가 500이면 예외 없이 빈 값을 돌려주고 중기예보는 부르지 않으며 대체 응답 지표를 올린다")
    void returnsEmptyWithoutCallingMidTermWhenShortTermFails() {
        // given
        stub(VILAGE_FCST_PATH, serverError());
        stub(MID_LAND_FCST_PATH, okJson(readFixture("mid-land-fcst.json")));
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")));
        double fallbackBefore = fallbackCount();

        // when
        Optional<WeatherForecast> result = service.forecast(60, 127, SEOUL_LAT, SEOUL_LNG);

        // then
        assertThat(result).isEmpty();
        assertThat(fallbackCount() - fallbackBefore).isEqualTo(1);
        assertThat(requestCount(MID_LAND_FCST_PATH)).isZero();
        assertThat(requestCount(MID_TA_PATH)).isZero();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청 응답이 응답 대기 시간보다 늦어도 예외 없이 빈 값을 돌려준다")
    void returnsEmptyWhenKmaIsSlow() {
        // given: 테스트 프로필의 응답 대기 시간은 1초다.
        stub(VILAGE_FCST_PATH, okJson(readFixture("vilage-fcst.json")).withFixedDelay(2_000));
        double fallbackBefore = fallbackCount();

        // when
        Optional<WeatherForecast> result = service.forecast(60, 127, SEOUL_LAT, SEOUL_LNG);

        // then
        assertThat(result).isEmpty();
        assertThat(fallbackCount() - fallbackBefore).isEqualTo(1);
        // WireMock이 지연 응답을 다 보낸 뒤에야 요청을 기록한다. 그래서 테스트는 기록이 두 건이 될 때까지 기다린다.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(2));
        assertThat(requestCount(MID_LAND_FCST_PATH)).isZero();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기상청이 잘못된 형식으로 응답해도 예외 없이 빈 값을 돌려준다")
    void returnsEmptyWhenResponseIsMalformed() {
        // given
        stub(VILAGE_FCST_PATH, okJson("{\"response\": {"));
        double fallbackBefore = fallbackCount();

        // when
        Optional<WeatherForecast> result = service.forecast(60, 127, SEOUL_LAT, SEOUL_LNG);

        // then
        assertThat(result).isEmpty();
        assertThat(fallbackCount() - fallbackBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 응답의 값이 숫자가 아니어도 예외 없이 빈 값을 돌려준다")
    void returnsEmptyWhenValueIsNotNumber() {
        // given
        stub(VILAGE_FCST_PATH, okJson("""
                {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                  "body": {"dataType": "JSON", "items": {"item": [
                    {"category": "TMP", "fcstDate": "20261004", "fcstTime": "1400", "fcstValue": "abc"}]},
                  "pageNo": 1, "numOfRows": 1000, "totalCount": 1}}}
                """));
        double fallbackBefore = fallbackCount();

        // when
        Optional<WeatherForecast> result = service.forecast(60, 127, SEOUL_LAT, SEOUL_LNG);

        // then
        assertThat(result).isEmpty();
        assertThat(fallbackCount() - fallbackBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 중기 육상예보가 실패하면 기온예보를 부르지 않고 빈 값을 돌려준다")
    void returnsEmptyWhenMidLandFails() {
        // given
        stub(VILAGE_FCST_PATH, okJson(readFixture("vilage-fcst.json")));
        stub(MID_LAND_FCST_PATH, serverError());
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")));

        // when
        Optional<WeatherForecast> result = service.forecast(60, 127, SEOUL_LAT, SEOUL_LNG);

        // then
        assertThat(result).isEmpty();
        assertThat(requestCount(MID_TA_PATH)).isZero();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 중기 기온예보가 실패해도 예외 없이 빈 값을 돌려준다")
    void returnsEmptyWhenMidTaFails() {
        // given
        stub(VILAGE_FCST_PATH, okJson(readFixture("vilage-fcst.json")));
        stub(MID_LAND_FCST_PATH, okJson(readFixture("mid-land-fcst.json")));
        stub(MID_TA_PATH, aResponse().withStatus(503));
        double fallbackBefore = fallbackCount();

        // when
        Optional<WeatherForecast> result = service.forecast(60, 127, SEOUL_LAT, SEOUL_LNG);

        // then
        assertThat(result).isEmpty();
        assertThat(fallbackCount() - fallbackBefore).isEqualTo(1);
    }

    private void stubAllSuccess() {
        stub(VILAGE_FCST_PATH, okJson(readFixture("vilage-fcst.json")));
        stub(MID_LAND_FCST_PATH, okJson(readFixture("mid-land-fcst.json")));
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")));
    }

    private void stub(String path, ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(path)).willReturn(response));
    }

    private int requestCount(String path) {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo(path))).size();
    }

    private double fallbackCount() {
        Counter counter = meterRegistry.find(FALLBACK_COUNTER).tag("api", "kma").counter();
        return counter == null ? 0 : counter.count();
    }

    private static String readFixture(String fileName) {
        try {
            return new ClassPathResource("fixtures/weather/kma/" + fileName).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
