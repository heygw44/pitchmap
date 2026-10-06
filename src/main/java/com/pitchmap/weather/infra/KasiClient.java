package com.pitchmap.weather.infra;

import com.pitchmap.common.external.ExternalApiMetrics;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * 한국천문연구원 출몰시각 API 클라이언트.
 *
 * <p>공공데이터포털은 하루 호출 수를 제한하고, 재시도한 요청도 이 한도에서 뺀다. 그래서 클라이언트는 다시 보내면 성공할 수 있는 실패(I/O
 * 오류, 타임아웃, HTTP 5xx)일 때만 한 번 더 보낸다. 반면 인증 오류나 원천 오류 코드는 다시 보내도 같은 결과라서 바로 실패로 끝낸다.
 *
 * <p>인증키는 쿼리 값으로 보내므로 요청 주소에 키가 들어 있다. 그래서 클라이언트는 요청 주소를 로그나 예외 메시지에 남기지 않는다. 또
 * 이 API는 XML만 주므로, 응답은 외부 엔티티와 DOCTYPE을 막은 XML 파서로 읽는다.
 */
@Slf4j
@Component
public class KasiClient {

    private static final String API = "kasi";
    private static final String RISE_SET = "getLCRiseSetInfo";
    private static final String RISE_SET_URI = "/RiseSetInfoService/getLCRiseSetInfo?serviceKey={serviceKey}"
            + "&locdate={locdate}&longitude={longitude}&latitude={latitude}&dnYn={dnYn}";
    // Y로 보내면 위도와 경도를 도·분이 아닌 십진수로 받는다.
    private static final String DECIMAL_COORDINATE = "Y";
    private static final String SUCCESS_RESULT_CODE = "00";
    private static final String GATEWAY_ERROR_ROOT = "OpenAPI_ServiceResponse";
    private static final String RESPONSE_ROOT = "response";
    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final String serviceKey;
    private final ExternalApiMetrics metrics;

    public KasiClient(KasiProperties properties, ExternalApiMetrics metrics) {
        this.restClient = createRestClient(properties);
        this.serviceKey = properties.serviceKey();
        this.metrics = metrics;
    }

    /**
     * 위치와 날짜의 일출·일몰·시민박명 시각을 받는다.
     *
     * @param date 조회할 날짜(한국 날짜)
     * @param lat 위도
     * @param lng 경도
     * @return 그날의 출몰시각. 원천이 항목을 주지 않았으면 빈 값
     * @throws KasiApiException 재시도까지 실패했거나, 원천이 오류를 돌려줬거나, 응답 형식을 해석하지 못했을 때
     */
    public Optional<KasiRiseSetItem> getRiseSet(LocalDate date, double lat, double lng) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("serviceKey", serviceKey);
        variables.put("locdate", LOCDATE.format(date));
        // Double.toString은 로케일과 상관없이 소수점으로 점을 쓴다.
        variables.put("longitude", Double.toString(lng));
        variables.put("latitude", Double.toString(lat));
        variables.put("dnYn", DECIMAL_COORDINATE);
        return fetch(variables, KasiClient::toItem);
    }

    private <T> T fetch(Map<String, Object> variables, Function<Element, T> parser) {
        try {
            return fetchOnce(variables, parser);
        } catch (KasiApiException e) {
            if (!e.isRetryable()) {
                throw e;
            }
            log.warn("천문연 API 호출이 실패해서 한 번 다시 보낸다. reason={}", e.getMessage());
            return fetchOnce(variables, parser);
        }
    }

    private <T> T fetchOnce(Map<String, Object> variables, Function<Element, T> parser) {
        return metrics.record(API, RISE_SET, () -> {
            RawResponse response = send(variables);
            return parser.apply(parseBody(response));
        });
    }

    private RawResponse send(Map<String, Object> variables) {
        try {
            return restClient.get().uri(RISE_SET_URI, variables).exchange((request, response) -> read(response));
        } catch (ResourceAccessException e) {
            // Spring은 이 예외 메시지에서 쿼리를 뺀 주소만 남긴다. 그래서 원인으로 이어도 메시지에 키가 들어가지 않는다.
            throw KasiApiException.retryable(
                    describe("I/O 오류 " + e.getMostSpecificCause().getClass().getSimpleName()), e);
        }
    }

    private static RawResponse read(ClientHttpResponse response) throws IOException {
        String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        return new RawResponse(response.getStatusCode(), body);
    }

    // 정상이면 응답 본문(response/body) 요소를 돌려준다. 그 밖의 응답은 모두 예외로 바꾼다.
    private Element parseBody(RawResponse response) {
        if (response.status().is5xxServerError()) {
            throw KasiApiException.retryable(
                    describe("HTTP " + response.status().value()));
        }
        Element root = parseXml(response);
        if (GATEWAY_ERROR_ROOT.equals(root.getTagName())) {
            throw new KasiApiException(describe("HTTP " + response.status().value() + ", " + gatewayErrorOf(root)));
        }
        if (!response.status().is2xxSuccessful()) {
            throw new KasiApiException(describe("HTTP " + response.status().value()));
        }
        if (!RESPONSE_ROOT.equals(root.getTagName())) {
            throw new KasiApiException(describe("XML 응답의 형식을 알 수 없다"));
        }
        String resultCode = textOf(root, "resultCode");
        if (!SUCCESS_RESULT_CODE.equals(resultCode)) {
            throw new KasiApiException(
                    describe("resultCode=" + resultCode + ", resultMsg=" + textOf(root, "resultMsg")));
        }
        return root;
    }

    private Element parseXml(RawResponse response) {
        try {
            return newDocumentBuilder()
                    .parse(new InputSource(new StringReader(response.body())))
                    .getDocumentElement();
        } catch (SAXException | IOException e) {
            // 오류 응답이 XML이 아닐 수도 있어서, 상태가 실패면 해석 실패보다 HTTP 상태를 먼저 알린다.
            if (!response.status().is2xxSuccessful()) {
                throw new KasiApiException(describe("HTTP " + response.status().value()), e);
            }
            throw new KasiApiException(describe("응답 XML을 해석하지 못했다"), e);
        }
    }

    // 외부에서 온 XML이므로 DOCTYPE과 외부 엔티티를 모두 막아 XXE와 엔티티 확장 공격을 차단한다.
    private static DocumentBuilder newDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new KasiApiException(describe("XML 파서를 설정하지 못했다"), e);
        }
    }

    // 인증키가 없거나 등록되지 않았으면, 공공데이터포털 게이트웨이는 resultCode 없는 XML을 돌려준다.
    // 예: <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
    //
    // <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>...</cmmMsgHeader></OpenAPI_ServiceResponse>
    private static String gatewayErrorOf(Element root) {
        return "인증 오류 returnAuthMsg=" + textOf(root, "returnAuthMsg") + ", errMsg=" + textOf(root, "errMsg");
    }

    // 결과가 없으면 items가 비어 있어서 item 요소가 없다. 항목이 여럿이어도 한 위치·날짜의 값은 하나라서 첫 항목만 읽는다.
    private static Optional<KasiRiseSetItem> toItem(Element root) {
        NodeList items = root.getElementsByTagName("item");
        if (items.getLength() == 0) {
            return Optional.empty();
        }
        Element item = (Element) items.item(0);
        return Optional.of(new KasiRiseSetItem(
                textOf(item, "locdate"), textOf(item, "sunrise"), textOf(item, "sunset"), textOf(item, "civile")));
    }

    // 값 뒤에 공백이 붙어서 온다. 요소가 없으면 null이다.
    private static String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node.getTextContent().strip();
    }

    private static String describe(String reason) {
        return "천문연 " + RISE_SET + " 호출 실패: " + reason;
    }

    private static RestClient createRestClient(KasiProperties properties) {
        // 공공데이터포털 Decoding 키에는 +, /, = 가 들어 있다. 키를 주소 문자열에 그대로 이어 붙이거나 URI_COMPONENT 방식으로 인코딩하면
        // +가 그대로 나가고, 서버는 +를 공백으로 읽어 인증에 실패한다. 그래서 클라이언트는 키를 URI 변수로 넘기고,
        // URI 변수 값의 예약 문자를 모두 퍼센트 인코딩하는 TEMPLATE_AND_VALUES 방식을 명시해서 고정한다.
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(properties.baseUrl());
        uriBuilderFactory.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);
        // JDK HttpClient는 평문 http 주소에도 HTTP/2 업그레이드를 시도한다. 천문연 API는 HTTP/1.1로 충분하므로 버전을 고정해서 이 협상을 없앤다.
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
