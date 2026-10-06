package com.pitchmap.weather.application;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.pitchmap.common.testsupport.IntegrationTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

@IntegrationTest
class SunQueryServiceIntegrationTest {

    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
    private static final String FALLBACK_COUNTER = "pitchmap.external.api.fallbacks";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
    // 서울시청
    private static final double LAT = 37.5665;
    private static final double LNG = 126.9780;

    @Autowired
    private SunQueryService service;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("[F-09][NFR-05] 천문연 응답이 정상이면 일출·일몰·시민박명 시각을 한국 시각으로 돌려준다")
    void returnsSunTimes() {
        // given
        stub(xml(readFixture("rise-set.xml")));

        // when
        Optional<SunTimes> result = service.sunTimes(LAT, LNG, DATE);

        // then
        assertThat(result)
                .contains(new SunTimes(DATE, LocalTime.of(6, 30), LocalTime.of(18, 11), LocalTime.of(18, 38)));
    }

    @Test
    @DisplayName("[F-09][NFR-05] 천문연이 500이면 예외 없이 빈 값을 돌려주고 대체 응답 지표를 올린다")
    void returnsEmptyWhenKasiFails() {
        // given
        stub(serverError());
        double fallbackBefore = fallbackCount();

        // when
        Optional<SunTimes> result = service.sunTimes(LAT, LNG, DATE);

        // then
        assertThat(result).isEmpty();
        assertThat(fallbackCount() - fallbackBefore).isEqualTo(1);
        assertThat(requestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 천문연 응답이 응답 대기 시간보다 늦어도 예외 없이 빈 값을 돌려준다")
    void returnsEmptyWhenKasiIsSlow() {
        // given: 테스트 프로필의 응답 대기 시간은 1초다.
        stub(xml(readFixture("rise-set.xml")).withFixedDelay(2_000));
        double fallbackBefore = fallbackCount();

        // when
        Optional<SunTimes> result = service.sunTimes(LAT, LNG, DATE);

        // then
        assertThat(result).isEmpty();
        assertThat(fallbackCount() - fallbackBefore).isEqualTo(1);
        // WireMock이 지연 응답을 다 보낸 뒤에야 요청을 기록한다. 그래서 테스트는 기록이 두 건이 될 때까지 기다린다.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(requestCount()).isEqualTo(2));
    }

    @Test
    @DisplayName("[F-09][NFR-05] 시각 값이 ----이면 예외 없이 빈 값을 돌려준다")
    void returnsEmptyWhenTimeIsMissingValue() {
        // given
        stub(xml(readFixture("rise-set.xml").replace("<sunrise>0630  </sunrise>", "<sunrise>----  </sunrise>")));
        double fallbackBefore = fallbackCount();

        // when
        Optional<SunTimes> result = service.sunTimes(LAT, LNG, DATE);

        // then
        assertThat(result).isEmpty();
        assertThat(fallbackCount() - fallbackBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 응답에 항목이 없으면 예외 없이 빈 값을 돌려준다")
    void returnsEmptyWhenItemIsMissing() {
        // given
        stub(xml("""
                <response><header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>
                <body><items/><numOfRows>10</numOfRows><pageNo>1</pageNo><totalCount>0</totalCount></body></response>
                """));
        double fallbackBefore = fallbackCount();

        // when
        Optional<SunTimes> result = service.sunTimes(LAT, LNG, DATE);

        // then
        assertThat(result).isEmpty();
        assertThat(fallbackCount() - fallbackBefore).isEqualTo(1);
    }

    private void stub(ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(RISE_SET_PATH)).willReturn(response));
    }

    private static ResponseDefinitionBuilder xml(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml;charset=UTF-8")
                .withBody(body);
    }

    private int requestCount() {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo(RISE_SET_PATH))).size();
    }

    private double fallbackCount() {
        Counter counter =
                meterRegistry.find(FALLBACK_COUNTER).tag("api", "kasi").counter();
        return counter == null ? 0 : counter.count();
    }

    private static String readFixture(String fileName) {
        try {
            return new ClassPathResource("fixtures/weather/kasi/" + fileName)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
