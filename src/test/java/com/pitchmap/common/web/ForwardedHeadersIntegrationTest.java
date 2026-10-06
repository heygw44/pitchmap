package com.pitchmap.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

// 프록시 헤더는 MockMvc가 아니라 Tomcat의 RemoteIpValve가 처리한다. 그래서 이 테스트만 실제 Tomcat을 띄우고 HTTP로 요청한다.
// 테스트 클라이언트는 127.0.0.1에서 접속하므로, Tomcat은 운영의 Caddy처럼 믿을 수 있는 내부 프록시로 본다.
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ForwardedHeadersIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.7";

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-02] 프록시가 X-Forwarded-For로 넘긴 실제 접속 IP를 로그인 기록에 남긴다")
    void loginHistoryRecordsForwardedClientIp() throws Exception {
        String email = TestSequence.email();
        String csrfToken = UUID.randomUUID().toString();
        HttpRequest request = HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .header("X-Forwarded-For", CLIENT_IP)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email + "\",\"password\":\"Wrong-pass-1!\"}"))
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(401);
        List<String> ips =
                jdbc.queryForList("SELECT ip FROM login_history WHERE attempted_email = ?", String.class, email);
        assertThat(ips).containsExactly(CLIENT_IP);
    }

    @Test
    @DisplayName("프록시가 X-Forwarded-Proto로 HTTPS라고 넘기면 서버가 CSRF 쿠키에 Secure를 붙인다")
    void csrfCookieIsSecureWhenProxyForwardsHttps() throws Exception {
        HttpResponse<String> overHttps = send(HttpRequest.newBuilder(uri("/actuator/health"))
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build());
        HttpResponse<String> overHttp =
                send(HttpRequest.newBuilder(uri("/actuator/health")).GET().build());

        assertThat(csrfCookie(overHttps)).contains("Secure");
        assertThat(csrfCookie(overHttp)).doesNotContain("Secure");
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String csrfCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("XSRF-TOKEN 쿠키가 없다"));
    }
}
