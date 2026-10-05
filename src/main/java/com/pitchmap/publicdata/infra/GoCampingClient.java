package com.pitchmap.publicdata.infra;

import com.pitchmap.common.external.ExternalApiMetrics;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 한국관광공사 고캠핑 API 클라이언트.
 *
 * <p>공공데이터포털은 하루 호출 수를 1,000번으로 제한하고, 재시도한 요청도 이 한도에서 뺀다. 그래서 클라이언트는 다시 보내면 성공할 수 있는
 * 실패(I/O 오류, 타임아웃, HTTP 5xx)일 때만 한 번 더 보낸다. 반면 인증 오류나 원천 오류 코드는 다시 보내도 같은 결과라서 바로 실패로 끝낸다.
 *
 * <p>전체 동기화 한 번은 페이지 크기 100 기준으로 약 54번을 호출한다(항목 5,353건).
 *
 * <p>인증키는 쿼리 값으로 보내므로 요청 주소에 키가 들어 있다. 그래서 클라이언트는 요청 주소를 로그나 예외 메시지에 남기지 않는다.
 */
@Slf4j
@Component
public class GoCampingClient {

    private static final String API = "gocamping";
    private static final String BASED_SYNC_LIST = "basedSyncList";
    private static final String BASED_SYNC_LIST_URI = "/basedSyncList?serviceKey={serviceKey}&MobileOS={mobileOs}"
            + "&MobileApp={mobileApp}&_type={type}&pageNo={pageNo}&numOfRows={numOfRows}";
    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "pitchmap";
    private static final String RESPONSE_TYPE = "json";
    private static final String SUCCESS_RESULT_CODE = "0000";
    private static final Pattern AUTH_ERROR_MESSAGE =
            Pattern.compile("<returnAuthMsg>\\s*([^<]*?)\\s*</returnAuthMsg>");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("<errMsg>\\s*([^<]*?)\\s*</errMsg>");

    private final RestClient restClient;
    private final String serviceKey;
    private final JsonMapper jsonMapper;
    private final ExternalApiMetrics metrics;

    public GoCampingClient(GoCampingProperties properties, JsonMapper jsonMapper, ExternalApiMetrics metrics) {
        this.restClient = createRestClient(properties);
        this.serviceKey = properties.serviceKey();
        this.jsonMapper = jsonMapper;
        this.metrics = metrics;
    }

    /**
     * 캠핑장 동기화 목록 한 페이지를 받는다.
     *
     * @param pageNo 1부터 세는 페이지 번호
     * @param numOfRows 페이지 크기
     * @throws GoCampingApiException 재시도까지 실패했거나, 원천이 오류를 돌려줬거나, 응답 형식을 해석하지 못했을 때
     */
    public GoCampingPage fetchPage(int pageNo, int numOfRows) {
        try {
            return fetchBasedSyncListOnce(pageNo, numOfRows);
        } catch (GoCampingApiException e) {
            if (!e.isRetryable()) {
                throw e;
            }
            log.warn("고캠핑 API 호출이 실패해서 한 번 다시 보낸다. reason={}", e.getMessage());
            return fetchBasedSyncListOnce(pageNo, numOfRows);
        }
    }

    private GoCampingPage fetchBasedSyncListOnce(int pageNo, int numOfRows) {
        return metrics.record(API, BASED_SYNC_LIST, () -> {
            RawResponse response = sendBasedSyncList(pageNo, numOfRows);
            return toPage(response, pageNo, numOfRows);
        });
    }

    private RawResponse sendBasedSyncList(int pageNo, int numOfRows) {
        Map<String, Object> uriVariables = Map.of(
                "serviceKey", serviceKey,
                "mobileOs", MOBILE_OS,
                "mobileApp", MOBILE_APP,
                "type", RESPONSE_TYPE,
                "pageNo", pageNo,
                "numOfRows", numOfRows);
        try {
            return restClient
                    .get()
                    .uri(BASED_SYNC_LIST_URI, uriVariables)
                    .exchange((request, response) -> read(response));
        } catch (ResourceAccessException e) {
            // Spring은 이 예외 메시지에서 쿼리를 뺀 주소만 남긴다. 그래서 원인으로 이어도 메시지에 키가 들어가지 않는다.
            throw GoCampingApiException.retryable(
                    describe(
                            pageNo,
                            "I/O 오류 " + e.getMostSpecificCause().getClass().getSimpleName()),
                    e);
        }
    }

    private static RawResponse read(ClientHttpResponse response) throws IOException {
        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        return new RawResponse(response.getStatusCode(), body);
    }

    private GoCampingPage toPage(RawResponse response, int pageNo, int numOfRows) {
        if (response.status().is5xxServerError()) {
            throw GoCampingApiException.retryable(
                    describe(pageNo, "HTTP " + response.status().value()));
        }
        if (looksLikeXml(response.body())) {
            throw new GoCampingApiException(
                    describe(pageNo, "HTTP " + response.status().value() + ", " + xmlErrorOf(response.body())));
        }
        if (!response.status().is2xxSuccessful()) {
            throw new GoCampingApiException(
                    describe(pageNo, "HTTP " + response.status().value()));
        }
        return parse(response.body(), pageNo, numOfRows);
    }

    private GoCampingPage parse(String body, int pageNo, int numOfRows) {
        JsonNode root = readTree(body, pageNo);
        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asString(null);
        if (!SUCCESS_RESULT_CODE.equals(resultCode)) {
            String resultMsg = header.path("resultMsg").asString(null);
            throw new GoCampingApiException(describe(pageNo, "resultCode=" + resultCode + ", resultMsg=" + resultMsg));
        }
        JsonNode responseBody = root.path("response").path("body");
        int totalCount = responseBody.path("totalCount").asInt(-1);
        if (totalCount < 0) {
            throw new GoCampingApiException(describe(pageNo, "응답에 totalCount가 없다"));
        }
        return new GoCampingPage(
                totalCount,
                responseBody.path("pageNo").asInt(pageNo),
                responseBody.path("numOfRows").asInt(numOfRows),
                itemsOf(responseBody.path("items"), pageNo));
    }

    private JsonNode readTree(String body, int pageNo) {
        try {
            return jsonMapper.readTree(body);
        } catch (JacksonException e) {
            throw new GoCampingApiException(describe(pageNo, "응답 JSON을 해석하지 못했다"), e);
        }
    }

    // 공공데이터포털은 결과가 없으면 items를 빈 문자열("")로 주고, 결과가 한 건이면 item을 배열이 아닌 객체 하나로 준다.
    // 그래서 클라이언트는 세 모양(빈 값, 객체 하나, 배열)을 모두 목록으로 바꾼다.
    private List<GoCampingItem> itemsOf(JsonNode items, int pageNo) {
        if (items.isMissingNode()
                || items.isNull()
                || (items.isString() && items.asString().isBlank())) {
            return List.of();
        }
        if (!items.isObject()) {
            throw new GoCampingApiException(describe(pageNo, "items 형식을 해석하지 못했다"));
        }
        JsonNode item = items.path("item");
        if (item.isMissingNode() || item.isNull()) {
            return List.of();
        }
        if (item.isObject()) {
            return List.of(toItem(item));
        }
        if (!item.isArray()) {
            throw new GoCampingApiException(describe(pageNo, "item 형식을 해석하지 못했다"));
        }
        List<GoCampingItem> result = new ArrayList<>(item.size());
        for (JsonNode element : item.values()) {
            result.add(toItem(element));
        }
        return result;
    }

    private static GoCampingItem toItem(JsonNode node) {
        return new GoCampingItem(
                textOf(node, "contentId"),
                textOf(node, "facltNm"),
                textOf(node, "addr1"),
                textOf(node, "addr2"),
                textOf(node, "mapX"),
                textOf(node, "mapY"),
                textOf(node, "induty"),
                textOf(node, "tel"),
                textOf(node, "homepage"),
                textOf(node, "toiletCo"),
                textOf(node, "swrmCo"),
                textOf(node, "wtrplCo"),
                textOf(node, "brazierCl"),
                textOf(node, "sbrsCl"),
                textOf(node, "sbrsEtc"),
                textOf(node, "posblFcltyCl"),
                textOf(node, "posblFcltyEtc"),
                textOf(node, "animalCmgCl"),
                textOf(node, "syncStatus"),
                textOf(node, "manageSttus"),
                textOf(node, "hvofBgnde"),
                textOf(node, "hvofEnddle"));
    }

    private static String textOf(JsonNode node, String field) {
        return node.path(field).asString(null);
    }

    private static boolean looksLikeXml(String body) {
        return body.stripLeading().startsWith("<");
    }

    // 인증키가 없거나 등록되지 않았으면, 공공데이터포털 게이트웨이는 _type=json을 무시하고 resultCode 없는 XML을 돌려준다.
    // 예: <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
    //
    // <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>...</cmmMsgHeader></OpenAPI_ServiceResponse>
    private static String xmlErrorOf(String body) {
        String authMessage = firstGroup(AUTH_ERROR_MESSAGE, body);
        String errorMessage = firstGroup(ERROR_MESSAGE, body);
        if (authMessage == null && errorMessage == null) {
            return "XML 응답을 해석하지 못했다";
        }
        return "인증 오류 returnAuthMsg=" + authMessage + ", errMsg=" + errorMessage;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String describe(int pageNo, String reason) {
        return "고캠핑 " + BASED_SYNC_LIST + " 호출 실패 pageNo=" + pageNo + ": " + reason;
    }

    private static RestClient createRestClient(GoCampingProperties properties) {
        // 공공데이터포털 Decoding 키에는 +, /, = 가 들어 있다. 키를 주소 문자열에 그대로 이어 붙이거나 URI_COMPONENT 방식으로 인코딩하면
        // +가 그대로 나가고, 서버는 +를 공백으로 읽어 인증에 실패한다. 그래서 클라이언트는 키를 URI 변수로 넘기고,
        // URI 변수 값의 예약 문자를 모두 퍼센트 인코딩하는 TEMPLATE_AND_VALUES 방식을 명시해서 고정한다.
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(properties.baseUrl());
        uriBuilderFactory.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);
        // JDK HttpClient는 평문 http 주소에도 HTTP/2 업그레이드를 시도한다. 고캠핑 API는 HTTP/1.1로 충분하므로 버전을 고정해서 이 협상을 없앤다.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .uriBuilderFactory(uriBuilderFactory)
                .requestFactory(requestFactory)
                .build();
    }

    private record RawResponse(HttpStatusCode status, String body) {}
}
