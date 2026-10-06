package com.pitchmap.spot.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.weather.application.SunQueryService;
import com.pitchmap.weather.application.WeatherQueryService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@IntegrationTest
class SpotDetailTransactionIntegrationTest {

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MutableClock clock;

    @MockitoSpyBean
    private WeatherQueryService weatherQueryService;

    @MockitoSpyBean
    private SunQueryService sunQueryService;

    @Test
    @DisplayName("[F-05][NFR-05] 기상청과 천문연을 부르는 동안에는 DB 트랜잭션이 열려 있지 않다")
    void callsExternalApisOutsideTransaction() {
        // given: 한국 시각 2026-10-04 12:58. 날씨 픽스처를 받은 시각이다.
        clock.setInstant(Instant.parse("2026-10-04T03:58:00Z"));
        String name = TestSequence.unique("야영장");
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('CAMPSITE', ?, ST_GeomFromText('POINT(37.5665 126.978)', 4326), 60, 127, 'ACTIVE',"
                        + " NOW(6), NOW(6))",
                name);
        long spotId = jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
        AtomicBoolean transactionDuringKma = new AtomicBoolean(true);
        AtomicBoolean transactionDuringKasi = new AtomicBoolean(true);
        stubAllSuccess();
        doAnswer(invocation -> {
                    transactionDuringKma.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return invocation.callRealMethod();
                })
                .when(weatherQueryService)
                .forecast(anyInt(), anyInt(), anyDouble(), anyDouble());
        doAnswer(invocation -> {
                    transactionDuringKasi.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return invocation.callRealMethod();
                })
                .when(sunQueryService)
                .sunTimes(anyDouble(), anyDouble(), any(LocalDate.class));

        // when
        spotDetailQueryService.findDetail(spotId);

        // then
        verify(weatherQueryService).forecast(anyInt(), anyInt(), anyDouble(), anyDouble());
        verify(sunQueryService).sunTimes(anyDouble(), anyDouble(), any(LocalDate.class));
        assertThat(transactionDuringKma).isFalse();
        assertThat(transactionDuringKasi).isFalse();
    }

    private void stubAllSuccess() {
        stub("/1360000/VilageFcstInfoService_2.0/getVilageFcst", okJson(readFixture("kma/vilage-fcst.json")));
        stub("/1360000/MidFcstInfoService/getMidLandFcst", okJson(readFixture("kma/mid-land-fcst.json")));
        stub("/1360000/MidFcstInfoService/getMidTa", okJson(readFixture("kma/mid-ta.json")));
        stub(
                "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo",
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody(readFixture("kasi/rise-set.xml")));
    }

    private void stub(String path, ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(path)).willReturn(response));
    }

    private static String readFixture(String path) {
        try {
            return new ClassPathResource("fixtures/weather/" + path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
