package com.pitchmap.publicdata.infra;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
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
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

// 테스트 프로필은 인증키를 "test+key/value=="로, 응답 대기 시간을 1초로 둔다.
// 인증키에 +, /, = 를 일부러 넣었으므로, 클라이언트가 퍼센트 인코딩을 빠뜨리면 키가 바뀌어 아래 인코딩 테스트가 실패한다.
@IntegrationTest
class GoCampingClientIntegrationTest {

    private static final String BASED_SYNC_LIST_PATH = "/B551011/GoCamping/basedSyncList";
    private static final String SERVICE_KEY = "test+key/value==";
    private static final String ENCODED_SERVICE_KEY = "test%2Bkey%2Fvalue%3D%3D";
    private static final String REQUEST_TIMER = "pitchmap.external.api.requests";
    private static final String RECOVERED = "recovered";

    @Autowired
    private GoCampingClient client;

    @Autowired
    private WireMockServer wireMock;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("[F-06] 고캠핑 동기화 목록 응답의 전체 개수와 캠핑장 항목, 동기화 상태, 운영 상태, 휴장 기간을 원천 값 그대로 읽는다")
    void readsTotalCountAndItemsFromBasedSyncListResponse() {
        // given
        stubBasedSyncList(okJson(readFixture("based-sync-list.json")));

        // when
        GoCampingPage page = client.fetchPage(1, 3);

        // then
        assertThat(page.totalCount()).isEqualTo(5353);
        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.numOfRows()).isEqualTo(3);
        assertThat(page.items()).hasSize(3);
        GoCampingItem first = page.items().getFirst();
        assertThat(first.contentId()).isEqualTo("102367");
        assertThat(first.facltNm()).isEqualTo("내산별빛캠핑장");
        assertThat(first.addr1()).isEqualTo("경기도 연천군 신서면 동내로 1402");
        assertThat(first.addr2()).isEmpty();
        // 원천에서 mapX는 경도, mapY는 위도다.
        assertThat(first.mapX()).isEqualTo("127.146834160271");
        assertThat(first.mapY()).isEqualTo("38.1569737100084");
        assertThat(first.induty()).isEqualTo("일반야영장");
        assertThat(first.toiletCo()).isEqualTo("0");
        assertThat(page.items())
                .extracting(GoCampingItem::contentId, GoCampingItem::syncStatus, GoCampingItem::manageSttus)
                .containsExactly(tuple("102367", "A", "운영"), tuple("1467", "U", "운영"), tuple("3466", "D", "휴장"));
        assertThat(page.items())
                .extracting(GoCampingItem::hvofBgnde, GoCampingItem::hvofEnddle)
                .containsExactly(tuple("", ""), tuple("2026-11-16", "2027-03-15"), tuple("2023-07-20", "2026-12-31"));
    }

    @Test
    @DisplayName("[F-06] 클라이언트는 인증키의 +, /, = 를 퍼센트 인코딩하고 페이지 번호와 크기를 쿼리로 보낸다")
    void sendsPercentEncodedServiceKeyWithPagingParameters() {
        // given
        stubBasedSyncList(okJson(readFixture("based-sync-list.json")));

        // when
        client.fetchPage(2, 3);

        // then
        wireMock.verify(getRequestedFor(urlPathEqualTo(BASED_SYNC_LIST_PATH))
                .withQueryParam("pageNo", equalTo("2"))
                .withQueryParam("numOfRows", equalTo("3"))
                .withQueryParam("_type", equalTo("json"))
                .withQueryParam("MobileOS", equalTo("ETC"))
                .withQueryParam("MobileApp", equalTo("pitchmap")));
        String rawServiceKey = rawQueryValue(onlyRequest(), "serviceKey");
        assertThat(rawServiceKey).isEqualTo(ENCODED_SERVICE_KEY);
        assertThat(URLDecoder.decode(rawServiceKey, StandardCharsets.UTF_8)).isEqualTo(SERVICE_KEY);
    }

    @Test
    @DisplayName("[F-06] 원천이 결과 없는 페이지의 items를 빈 문자열로 주면 빈 목록을 돌려준다")
    void returnsEmptyItemsWhenItemsIsEmptyString() {
        // given
        stubBasedSyncList(okJson("""
                {"response": {"header": {"resultCode": "0000", "resultMsg": "OK"},
                  "body": {"items": "", "numOfRows": 2, "pageNo": 1559, "totalCount": 3115}}}
                """));

        // when
        GoCampingPage page = client.fetchPage(1559, 2);

        // then
        assertThat(page.totalCount()).isEqualTo(3115);
        assertThat(page.pageNo()).isEqualTo(1559);
        assertThat(page.items()).isEmpty();
    }

    @Test
    @DisplayName("[F-06] 원천이 결과 한 건을 배열이 아닌 객체로 주어도 항목 하나로 읽는다")
    void readsSingleItemObjectAsOneItem() {
        // given
        stubBasedSyncList(okJson("""
                {"response": {"header": {"resultCode": "0000", "resultMsg": "OK"},
                  "body": {"items": {"item": {"contentId": "146", "facltNm": "강변사리 캠핑장",
                    "mapX": "127.190085215523", "mapY": "35.4952105728394"}},
                  "numOfRows": 2, "pageNo": 1558, "totalCount": 3115}}}
                """));

        // when
        GoCampingPage page = client.fetchPage(1558, 2);

        // then
        assertThat(page.items()).hasSize(1);
        GoCampingItem item = page.items().getFirst();
        assertThat(item.contentId()).isEqualTo("146");
        assertThat(item.mapX()).isEqualTo("127.190085215523");
        assertThat(item.mapY()).isEqualTo("35.4952105728394");
        assertThat(item.addr1()).isNull();
    }

    @Test
    @DisplayName("[F-06][NFR-06] 원천이 성공이 아닌 resultCode를 주면 다시 보내지 않고 오류 코드를 담은 예외를 던진다")
    void throwsWithoutRetryWhenResultCodeIsNotSuccess() {
        // given
        stubBasedSyncList(okJson("""
                {"response": {"header": {"resultCode": "10", "resultMsg": "INVALID_REQUEST_PARAMETER_ERROR"}}}
                """));

        // when
        GoCampingApiException thrown = catchThrowableOfType(GoCampingApiException.class, () -> client.fetchPage(1, 2));

        // then
        assertThat(thrown)
                .hasMessageContaining("resultCode=10")
                .hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");
        assertThat(basedSyncListRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06][NFR-06] 인증 오류 XML을 받으면 다시 보내지 않고 인증 오류 내용을 담은 예외를 던진다")
    void throwsWithoutRetryWhenAuthErrorXmlIsReturned() {
        // given
        stubBasedSyncList(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml;charset=UTF-8")
                .withBody(readFixture("auth-error.xml")));

        // when
        GoCampingApiException thrown = catchThrowableOfType(GoCampingApiException.class, () -> client.fetchPage(1, 2));

        // then
        assertThat(thrown).hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
        assertNoServiceKey(thrown);
        assertThat(basedSyncListRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06][NFR-06] 첫 요청이 500이면 한 번 다시 보내고, 두 요청을 모두 지표에 남긴다")
    void retriesOnceAfterServerErrorAndRecordsBothAttempts() {
        // given
        wireMock.stubFor(get(urlPathEqualTo(BASED_SYNC_LIST_PATH))
                .inScenario("server error once")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(serverError())
                .willSetStateTo(RECOVERED));
        wireMock.stubFor(get(urlPathEqualTo(BASED_SYNC_LIST_PATH))
                .inScenario("server error once")
                .whenScenarioStateIs(RECOVERED)
                .willReturn(okJson(readFixture("based-sync-list.json"))));
        long successBefore = requestCount("success");
        long failureBefore = requestCount("failure");

        // when
        GoCampingPage page = client.fetchPage(1, 3);

        // then
        assertThat(page.totalCount()).isEqualTo(5353);
        assertThat(basedSyncListRequestCount()).isEqualTo(2);
        assertThat(requestCount("success") - successBefore).isEqualTo(1);
        assertThat(requestCount("failure") - failureBefore).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-06][NFR-06] 500이 두 번 이어지면 두 번째 요청 뒤에 인증키 없는 예외를 던진다")
    void throwsAfterSecondServerError() {
        // given
        stubBasedSyncList(serverError());
        long successBefore = requestCount("success");
        long failureBefore = requestCount("failure");

        // when
        GoCampingApiException thrown = catchThrowableOfType(GoCampingApiException.class, () -> client.fetchPage(1, 2));

        // then
        assertThat(thrown).hasMessageContaining("HTTP 500");
        assertNoServiceKey(thrown);
        assertThat(basedSyncListRequestCount()).isEqualTo(2);
        assertThat(requestCount("success") - successBefore).isZero();
        assertThat(requestCount("failure") - failureBefore).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-06][NFR-06] 응답이 응답 대기 시간보다 늦으면 한 번 다시 보낸 뒤 인증키 없는 예외를 던진다")
    void throwsAfterSecondReadTimeout() {
        // given: 테스트 프로필의 응답 대기 시간은 1초다.
        stubBasedSyncList(okJson(readFixture("based-sync-list.json")).withFixedDelay(2_000));
        long failureBefore = requestCount("failure");

        // when
        GoCampingApiException thrown = catchThrowableOfType(GoCampingApiException.class, () -> client.fetchPage(1, 2));

        // then
        assertThat(thrown).hasMessageContaining("I/O 오류");
        assertNoServiceKey(thrown);
        assertThat(requestCount("failure") - failureBefore).isEqualTo(2);
        // WireMock이 지연 응답을 다 보낸 뒤에야 요청을 기록할 수 있다. 그러면 기록 시점이 클라이언트 예외보다 늦으므로, 테스트는 기록이 두 건이 될 때까지 기다린다.
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(basedSyncListRequestCount()).isEqualTo(2));
    }

    private void stubBasedSyncList(ResponseDefinitionBuilder response) {
        wireMock.stubFor(get(urlPathEqualTo(BASED_SYNC_LIST_PATH)).willReturn(response));
    }

    private int basedSyncListRequestCount() {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo(BASED_SYNC_LIST_PATH)))
                .size();
    }

    private LoggedRequest onlyRequest() {
        var requests = wireMock.findAll(getRequestedFor(urlPathEqualTo(BASED_SYNC_LIST_PATH)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private long requestCount(String outcome) {
        Timer timer = meterRegistry
                .find(REQUEST_TIMER)
                .tags("api", "gocamping", "operation", "basedSyncList", "outcome", outcome)
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
            return new ClassPathResource("fixtures/publicdata/gocamping/" + fileName)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
