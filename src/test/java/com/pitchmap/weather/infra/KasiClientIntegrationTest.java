package com.pitchmap.weather.infra;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.pitchmap.common.testsupport.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

@IntegrationTest
class KasiClientIntegrationTest {

    private static final String RISE_SET_PATH = "/B090041/openapi/service/RiseSetInfoService/getLCRiseSetInfo";
    private static final String SERVICE_KEY = "test+key/value==";
    private static final String ENCODED_SERVICE_KEY = "test%2Bkey%2Fvalue%3D%3D";
    private static final String REQUEST_TIMER = "pitchmap.external.api.requests";
    private static final String OPERATION = "getLCRiseSetInfo";
    private static final String RECOVERED = "recovered";
    private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
    // 서울시청
    private static final double LAT = 37.5665;
    private static final double LNG = 126.9780;

    @Autowired
    private KasiClient client;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("[F-09] 출몰시각 응답의 항목을 값 뒤 공백만 빼고 원천 값 그대로 읽는다")
    void readsRiseSetItem() {
        // given
        stub(xml(readFixture("rise-set.xml")));

        // when
        Optional<KasiRiseSetItem> item = client.getRiseSet(DATE, LAT, LNG);

        // then
        assertThat(item).contains(new KasiRiseSetItem("20261004", "0630", "1811", "1838"));
    }

    @Test
    @DisplayName("[F-09] 출몰시각 요청은 날짜, 위도, 경도, 십진수 좌표 표시를 쿼리로 보낸다")
    void sendsRiseSetQuery() {
        // given
        stub(xml(readFixture("rise-set.xml")));

        // when
        client.getRiseSet(DATE, LAT, LNG);

        // then
        wireMock.verify(getRequestedFor(urlPathEqualTo(RISE_SET_PATH))
                .withQueryParam("locdate", equalTo("20261004"))
                .withQueryParam("latitude", equalTo("37.5665"))
                .withQueryParam("longitude", equalTo("126.978"))
                .withQueryParam("dnYn", equalTo("Y")));
    }

    @Test
    @DisplayName("[F-09][NFR-06] 클라이언트는 인증키의 +, /, = 를 퍼센트 인코딩해서 보낸다")
    void sendsPercentEncodedServiceKey() {
        // given
        stub(xml(readFixture("rise-set.xml")));

        // when
        client.getRiseSet(DATE, LAT, LNG);

        // then
        String rawServiceKey = rawQueryValue(onlyRequest(), "serviceKey");
        assertThat(rawServiceKey).isEqualTo(ENCODED_SERVICE_KEY);
        assertThat(URLDecoder.decode(rawServiceKey, StandardCharsets.UTF_8)).isEqualTo(SERVICE_KEY);
    }

    @Test
    @DisplayName("[F-09] 응답에 항목이 없으면 빈 값을 돌려준다")
    void returnsEmptyWhenItemsAreMissing() {
        // given
        stub(xml("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?><response><header><resultCode>00</resultCode>
                <resultMsg>NORMAL SERVICE.</resultMsg></header><body><items/><numOfRows>10</numOfRows>
                <pageNo>1</pageNo><totalCount>0</totalCount></body></response>
                """));

        // when
        Optional<KasiRiseSetItem> item = client.getRiseSet(DATE, LAT, LNG);

        // then
        assertThat(item).isEmpty();
    }

    @Test
    @DisplayName("[F-09][NFR-06] 원천이 성공이 아닌 resultCode를 주면 다시 보내지 않고 오류 코드를 담은 예외를 던진다")
    void throwsWithoutRetryWhenResultCodeIsNotSuccess() {
        // given
        stub(xml("""
                <response><header><resultCode>10</resultCode>
                <resultMsg>INVALID_REQUEST_PARAMETER_ERROR</resultMsg></header></response>
                """));

        // when
        KasiApiException thrown = catchThrowableOfType(KasiApiException.class, () -> client.getRiseSet(DATE, LAT, LNG));

        // then
        assertThat(thrown)
                .hasMessageContaining("resultCode=10")
                .hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 인증 오류 XML을 받으면 다시 보내지 않고 인증 오류 내용을 담은 예외를 던진다")
    void throwsWithoutRetryWhenAuthErrorXmlIsReturned() {
        // given
        stub(xml("""
                <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                <returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
                """));

        // when
        KasiApiException thrown = catchThrowableOfType(KasiApiException.class, () -> client.getRiseSet(DATE, LAT, LNG));

        // then
        assertThat(thrown).hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
        assertNoServiceKey(thrown);
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 응답 XML이 깨졌으면 다시 보내지 않고 예외를 던진다")
    void throwsWithoutRetryWhenXmlIsBroken() {
        // given
        stub(xml("<response><header><resultCode>00"));

        // when
        KasiApiException thrown = catchThrowableOfType(KasiApiException.class, () -> client.getRiseSet(DATE, LAT, LNG));

        // then
        assertThat(thrown).hasMessageContaining("XML");
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 응답에 DOCTYPE이 있으면 외부 엔티티를 읽지 않고 다시 보내지 않은 채 예외를 던진다")
    void throwsWithoutRetryWhenXmlHasDoctype() {
        // given
        stub(xml("""
                <?xml version="1.0"?>
                <!DOCTYPE response [<!ENTITY secret SYSTEM "file:///etc/hosts">]>
                <response><header><resultCode>00</resultCode><resultMsg>&secret;</resultMsg></header></response>
                """));

        // when
        KasiApiException thrown = catchThrowableOfType(KasiApiException.class, () -> client.getRiseSet(DATE, LAT, LNG));

        // then
        assertThat(thrown).hasMessageContaining("XML");
        assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 첫 요청이 500이면 한 번 다시 보내고, 두 요청을 모두 지표에 남긴다")
    void retriesOnceAfterServerErrorAndRecordsBothAttempts() {
        // given
        wireMock.stubFor(get(urlPathEqualTo(RISE_SET_PATH))
                .inScenario("server error once")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(serverError())
                .willSetStateTo(RECOVERED));
        wireMock.stubFor(get(urlPathEqualTo(RISE_SET_PATH))
                .inScenario("server error once")
                .whenScenarioStateIs(RECOVERED)
                .willReturn(xml(readFixture("rise-set.xml"))));
        long successBefore = timerCount("success");
        long failureBefore = timerCount("failure");

        // when
        Optional<KasiRiseSetItem> item = client.getRiseSet(DATE, LAT, LNG);

        // then
        assertThat(item).isPresent();
        assertThat(requestCount()).isEqualTo(2);
        assertThat(timerCount("success") - successBefore).isEqualTo(1);
        assertThat(timerCount("failure") - failureBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 500이 두 번 이어지면 두 번째 요청 뒤에 인증키 없는 예외를 던진다")
    void throwsAfterSecondServerError() {
        // given
        stub(serverError());
        long failureBefore = timerCount("failure");

        // when
        KasiApiException thrown = catchThrowableOfType(KasiApiException.class, () -> client.getRiseSet(DATE, LAT, LNG));

        // then
        assertThat(thrown).hasMessageContaining("HTTP 500");
        assertNoServiceKey(thrown);
        assertThat(requestCount()).isEqualTo(2);
        assertThat(timerCount("failure") - failureBefore).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 응답이 응답 대기 시간보다 늦으면 한 번 다시 보낸 뒤 인증키 없는 예외를 던진다")
    void throwsAfterSecondReadTimeout() {
        // given: 테스트 프로필의 응답 대기 시간은 1초다.
        stub(xml(readFixture("rise-set.xml")).withFixedDelay(2_000));
        long failureBefore = timerCount("failure");

        // when
        KasiApiException thrown = catchThrowableOfType(KasiApiException.class, () -> client.getRiseSet(DATE, LAT, LNG));

        // then
        assertThat(thrown).hasMessageContaining("I/O 오류");
        assertNoServiceKey(thrown);
        assertThat(timerCount("failure") - failureBefore).isEqualTo(2);
        // WireMock이 지연 응답을 다 보낸 뒤에야 요청을 기록한다. 그래서 테스트는 기록이 두 건이 될 때까지 기다린다.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(requestCount()).isEqualTo(2));
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

    private LoggedRequest onlyRequest() {
        var requests = wireMock.findAll(getRequestedFor(urlPathEqualTo(RISE_SET_PATH)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private long timerCount(String outcome) {
        Timer timer = meterRegistry
                .find(REQUEST_TIMER)
                .tags("api", "kasi", "operation", OPERATION, "outcome", outcome)
                .timer();
        return timer == null ? 0 : timer.count();
    }

    private static String rawQueryValue(LoggedRequest request, String name) {
        String rawQuery = URI.create(request.getUrl()).getRawQuery();
        return Arrays.stream(rawQuery.split("&"))
                .filter(pair -> pair.startsWith(name + "="))
                .map(pair -> pair.substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " 쿼리가 없다"));
    }

    // 키가 메시지에 남으면 로그와 오류 추적 도구로 비밀값이 새어 나간다. 그래서 원인 예외까지 모두 확인한다.
    private static void assertNoServiceKey(Throwable thrown) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            assertThat(String.valueOf(current.getMessage())).doesNotContain(SERVICE_KEY, ENCODED_SERVICE_KEY);
        }
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
