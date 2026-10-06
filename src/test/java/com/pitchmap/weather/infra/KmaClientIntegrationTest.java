package com.pitchmap.weather.infra;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

@IntegrationTest
class KmaClientIntegrationTest {

    private static final String VILAGE_FCST_PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String MID_LAND_FCST_PATH = "/1360000/MidFcstInfoService/getMidLandFcst";
    private static final String MID_TA_PATH = "/1360000/MidFcstInfoService/getMidTa";
    private static final String SERVICE_KEY = "test+key/value==";
    private static final String ENCODED_SERVICE_KEY = "test%2Bkey%2Fvalue%3D%3D";
    private static final String REQUEST_TIMER = "pitchmap.external.api.requests";
    private static final String RECOVERED = "recovered";
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 4, 5, 0);
    private static final LocalDateTime TM_FC = LocalDateTime.of(2026, 10, 4, 6, 0);

    @Autowired
    private KmaClient client;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("[F-09] 단기예보 응답의 항목을 원천 값 그대로 읽는다")
    void readsShortTermItems() {
        // given
        stub(VILAGE_FCST_PATH, okJson(readFixture("vilage-fcst.json")));

        // when
        List<KmaShortTermItem> items = client.getVilageFcst(BASE, 60, 127);

        // then
        assertThat(items).hasSize(907);
        assertThat(items.getFirst()).isEqualTo(new KmaShortTermItem("TMP", "20261004", "0600", "15"));
    }

    @Test
    @DisplayName("[F-09] 단기예보 요청은 발표 날짜·시각, 격자, 페이지 크기를 쿼리로 보낸다")
    void sendsShortTermQuery() {
        // given
        stub(VILAGE_FCST_PATH, okJson(readFixture("vilage-fcst.json")));

        // when
        client.getVilageFcst(BASE, 60, 127);

        // then
        wireMock.verify(getRequestedFor(urlPathEqualTo(VILAGE_FCST_PATH))
                .withQueryParam("base_date", equalTo("20261004"))
                .withQueryParam("base_time", equalTo("0500"))
                .withQueryParam("nx", equalTo("60"))
                .withQueryParam("ny", equalTo("127"))
                .withQueryParam("pageNo", equalTo("1"))
                .withQueryParam("numOfRows", equalTo("1000"))
                .withQueryParam("dataType", equalTo("JSON")));
    }

    @Test
    @DisplayName("[F-09] 중기 육상예보와 기온예보 응답의 필드를 원천 값 그대로 읽는다")
    void readsMidTermItems() {
        // given
        stub(MID_LAND_FCST_PATH, okJson(readFixture("mid-land-fcst.json")));
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")));

        // when
        Optional<KmaMidTermItem> land = client.getMidLandFcst("11B00000", TM_FC);
        Optional<KmaMidTermItem> ta = client.getMidTa("11B10101", TM_FC);

        // then
        assertThat(land).isPresent();
        assertThat(land.get().get("regId")).isEqualTo("11B00000");
        assertThat(land.get().get("wf4Am")).isEqualTo("맑음");
        assertThat(land.get().get("rnSt8")).isEqualTo("20");
        assertThat(ta).isPresent();
        assertThat(ta.get().get("taMin4")).isEqualTo("10");
        assertThat(ta.get().get("taMax10")).isEqualTo("21");
        wireMock.verify(getRequestedFor(urlPathEqualTo(MID_LAND_FCST_PATH))
                .withQueryParam("regId", equalTo("11B00000"))
                .withQueryParam("tmFc", equalTo("202610040600"))
                .withQueryParam("dataType", equalTo("JSON")));
        wireMock.verify(getRequestedFor(urlPathEqualTo(MID_TA_PATH)).withQueryParam("regId", equalTo("11B10101")));
    }

    @Test
    @DisplayName("[F-09] 중기예보 응답에 항목이 없으면 빈 값을 돌려준다")
    void returnsEmptyWhenMidTermItemsAreMissing() {
        // given
        stub(MID_TA_PATH, okJson("""
                {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                  "body": {"dataType": "JSON", "items": "", "pageNo": 1, "numOfRows": 10, "totalCount": 0}}}
                """));

        // when
        Optional<KmaMidTermItem> ta = client.getMidTa("11B10101", TM_FC);

        // then
        assertThat(ta).isEmpty();
    }

    @Test
    @DisplayName("[F-09][NFR-06] 클라이언트는 인증키의 +, /, = 를 퍼센트 인코딩해서 보낸다")
    void sendsPercentEncodedServiceKey() {
        // given
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")));

        // when
        client.getMidTa("11B10101", TM_FC);

        // then
        String rawServiceKey = rawQueryValue(onlyRequest(MID_TA_PATH), "serviceKey");
        assertThat(rawServiceKey).isEqualTo(ENCODED_SERVICE_KEY);
        assertThat(URLDecoder.decode(rawServiceKey, StandardCharsets.UTF_8)).isEqualTo(SERVICE_KEY);
    }

    @Test
    @DisplayName("[F-09] 단기예보 전체 개수가 받은 항목 수보다 크면 일부만 받은 응답이라 실패로 본다")
    void throwsWhenShortTermResponseIsIncomplete() {
        // given
        stub(VILAGE_FCST_PATH, okJson("""
                {"response": {"header": {"resultCode": "00", "resultMsg": "NORMAL_SERVICE"},
                  "body": {"dataType": "JSON", "items": {"item": [
                    {"category": "TMP", "fcstDate": "20261004", "fcstTime": "0600", "fcstValue": "15"}]},
                  "pageNo": 1, "numOfRows": 1000, "totalCount": 1500}}}
                """));

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getVilageFcst(BASE, 60, 127));

        // then
        assertThat(thrown).hasMessageContaining("totalCount=1500");
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 원천이 성공이 아닌 resultCode를 주면 다시 보내지 않고 오류 코드를 담은 예외를 던진다")
    void throwsWithoutRetryWhenResultCodeIsNotSuccess() {
        // given
        stub(MID_TA_PATH, okJson("""
                {"response": {"header": {"resultCode": "10", "resultMsg": "INVALID_REQUEST_PARAMETER_ERROR"}}}
                """));

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getMidTa("11B10101", TM_FC));

        // then
        assertThat(thrown)
                .hasMessageContaining("resultCode=10")
                .hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 응답 JSON이 깨졌으면 다시 보내지 않고 예외를 던진다")
    void throwsWithoutRetryWhenJsonIsBroken() {
        // given
        stub(MID_TA_PATH, okJson("{\"response\": {"));

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getMidTa("11B10101", TM_FC));

        // then
        assertThat(thrown).hasMessageContaining("JSON");
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 인증 오류 XML을 받으면 다시 보내지 않고 인증 오류 내용을 담은 예외를 던진다")
    void throwsWithoutRetryWhenAuthErrorXmlIsReturned() {
        // given
        stub(
                MID_TA_PATH,
                aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody("""
                        <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                        <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                        <returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
                        """));

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getMidTa("11B10101", TM_FC));

        // then
        assertThat(thrown).hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
        assertNoServiceKey(thrown);
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 첫 요청이 500이면 한 번 다시 보내고, 두 요청을 모두 지표에 남긴다")
    void retriesOnceAfterServerErrorAndRecordsBothAttempts() {
        // given
        wireMock.stubFor(get(urlPathEqualTo(MID_TA_PATH))
                .inScenario("server error once")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(serverError())
                .willSetStateTo(RECOVERED));
        wireMock.stubFor(get(urlPathEqualTo(MID_TA_PATH))
                .inScenario("server error once")
                .whenScenarioStateIs(RECOVERED)
                .willReturn(okJson(readFixture("mid-ta.json"))));
        long successBefore = timerCount("getMidTa", "success");
        long failureBefore = timerCount("getMidTa", "failure");

        // when
        Optional<KmaMidTermItem> ta = client.getMidTa("11B10101", TM_FC);

        // then
        assertThat(ta).isPresent();
        assertThat(requestCount(MID_TA_PATH)).isEqualTo(2);
        assertThat(timerCount("getMidTa", "success") - successBefore).isEqualTo(1);
        assertThat(timerCount("getMidTa", "failure") - failureBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-09][NFR-06] 500이 두 번 이어지면 두 번째 요청 뒤에 인증키 없는 예외를 던진다")
    void throwsAfterSecondServerError() {
        // given
        stub(VILAGE_FCST_PATH, serverError());
        long failureBefore = timerCount("getVilageFcst", "failure");

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getVilageFcst(BASE, 60, 127));

        // then
        assertThat(thrown).hasMessageContaining("HTTP 500");
        assertNoServiceKey(thrown);
        assertThat(requestCount(VILAGE_FCST_PATH)).isEqualTo(2);
        assertThat(timerCount("getVilageFcst", "failure") - failureBefore).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-09][NFR-05] 응답이 응답 대기 시간보다 늦으면 한 번 다시 보낸 뒤 인증키 없는 예외를 던진다")
    void throwsAfterSecondReadTimeout() {
        // given: 테스트 프로필의 응답 대기 시간은 1초다.
        stub(MID_TA_PATH, okJson(readFixture("mid-ta.json")).withFixedDelay(2_000));
        long failureBefore = timerCount("getMidTa", "failure");

        // when
        KmaApiException thrown = catchThrowableOfType(KmaApiException.class, () -> client.getMidTa("11B10101", TM_FC));

        // then
        assertThat(thrown).hasMessageContaining("I/O 오류");
        assertNoServiceKey(thrown);
        assertThat(timerCount("getMidTa", "failure") - failureBefore).isEqualTo(2);
        // WireMock이 지연 응답을 다 보낸 뒤에야 요청을 기록한다. 그래서 테스트는 기록이 두 건이 될 때까지 기다린다.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(requestCount(MID_TA_PATH)).isEqualTo(2));
    }

    private void stub(String path, ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(path)).willReturn(response));
    }

    private int requestCount(String path) {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo(path))).size();
    }

    private LoggedRequest onlyRequest(String path) {
        var requests = wireMock.findAll(getRequestedFor(urlPathEqualTo(path)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private long timerCount(String operation, String outcome) {
        Timer timer = meterRegistry
                .find(REQUEST_TIMER)
                .tags("api", "kma", "operation", operation, "outcome", outcome)
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
            return new ClassPathResource("fixtures/weather/kma/" + fileName).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
