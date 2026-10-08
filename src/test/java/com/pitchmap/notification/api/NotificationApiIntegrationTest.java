package com.pitchmap.notification.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class NotificationApiIntegrationTest {

    private static final String PATH = "/api/me/notifications";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private Member owner;
    private Cookie ownerSession;
    private Member other;
    private Cookie otherSession;

    @BeforeEach
    void setUp() {
        owner = memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build());
        other = memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build());
        ownerSession = login(owner);
        otherSession = login(other);
    }

    @Test
    @DisplayName("[F-20] 알림 목록은 최신순이고 다른 회원의 알림은 담지 않으며 size만큼 끊어 hasNext를 알린다")
    void listIsNewestFirstAndOwnOnly() {
        // given
        long first = insert(owner, "n1", false);
        long second = insert(owner, "n2", false);
        long third = insert(owner, "n3", false);
        insert(other, "n4", false);

        // when
        MvcTestResult all = get(ownerSession, PATH);
        MvcTestResult firstPage = get(ownerSession, PATH + "?page=0&size=2");
        MvcTestResult secondPage = get(ownerSession, PATH + "?page=1&size=2");

        // then
        assertThat(all).hasStatus(HttpStatus.OK);
        assertThat(ids(all)).containsExactly(third, second, first);
        assertThat(all).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(ids(firstPage)).containsExactly(third, second);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(ids(secondPage)).containsExactly(first);
        assertThat(secondPage).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(secondPage).bodyJson().extractingPath("$.page").isEqualTo(1);
    }

    @Test
    @DisplayName("[F-20] 안 읽은 알림 수는 내 알림 중 읽지 않은 것만 센다")
    void unreadCountIsOwnUnreadOnly() {
        // given
        insert(owner, "n1", false);
        insert(owner, "n2", true);
        insert(other, "n3", false);

        // when
        MvcTestResult result = get(ownerSession, PATH + "/unread-count");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"count\": 1 }");
    }

    @Test
    @DisplayName("[F-20] 다른 회원의 알림이나 없는 알림을 읽음 처리하면 404 NOT_FOUND이고 알림은 그대로다")
    void readOfOthersOrUnknownNotificationIsNotFound() {
        // given
        long othersNotification = insert(other, "n1", false);

        // when
        MvcTestResult others = post(ownerSession, PATH + "/" + othersNotification + "/read");
        MvcTestResult unknown = post(ownerSession, PATH + "/" + (othersNotification + 1000) + "/read");

        // then
        assertThat(others).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(others).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(readAt(othersNotification)).isNull();
    }

    @Test
    @DisplayName("[F-20] 읽음 처리는 204이고 이미 읽은 알림을 다시 읽어도 204이며 처음 읽은 시각을 유지한다")
    void readIsIdempotentAndKeepsFirstReadAt() {
        // given
        long notificationId = insert(owner, "n1", false);
        LocalDateTime firstReadAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        // when
        MvcTestResult first = post(ownerSession, PATH + "/" + notificationId + "/read");
        clock.advance(Duration.ofHours(1));
        MvcTestResult second = post(ownerSession, PATH + "/" + notificationId + "/read");

        // then
        assertThat(first).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(second).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(readAt(notificationId)).isEqualTo(firstReadAt);
        assertThat(get(ownerSession, PATH))
                .bodyJson()
                .extractingPath("$.content[0].readAt")
                .isNotNull();
    }

    @Test
    @DisplayName("[F-20] 모두 읽음 처리하면 204이고 안 읽은 알림 수가 0이 되며 다른 회원의 알림은 그대로다")
    void readAllClearsOwnUnreadOnly() {
        // given
        insert(owner, "n1", false);
        insert(owner, "n2", false);
        long othersNotification = insert(other, "n3", false);

        // when
        MvcTestResult result = post(ownerSession, PATH + "/read-all");

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(get(ownerSession, PATH + "/unread-count"))
                .bodyJson()
                .extractingPath("$.count")
                .isEqualTo(0);
        assertThat(get(otherSession, PATH + "/unread-count"))
                .bodyJson()
                .extractingPath("$.count")
                .isEqualTo(1);
        assertThat(readAt(othersNotification)).isNull();
    }

    @Test
    @DisplayName("[F-20] 로그인하지 않으면 목록과 읽음 처리 모두 401 AUTHENTICATION_REQUIRED다")
    void anonymousIsUnauthorized() {
        MvcTestResult list = mvc.get().uri(PATH).exchange();
        MvcTestResult read = mvc.post().uri(PATH + "/1/read").with(csrf()).exchange();

        assertThat(list).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(read).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(read).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    private long insert(Member member, String dedupKey, boolean read) {
        jdbc.update(
                "INSERT INTO notification (member_id, type, title, body, link, dedup_key, read_at, created_at) "
                        + "VALUES (?, 'BASECAMP_APPROVED', '합류 승인', '승인됐습니다.', '/basecamps/1', ?, "
                        + (read ? "UTC_TIMESTAMP(6)" : "NULL") + ", UTC_TIMESTAMP(6))",
                member.getId(),
                dedupKey);
        return jdbc.queryForObject("SELECT MAX(id) FROM notification", Long.class);
    }

    private LocalDateTime readAt(long notificationId) {
        return jdbc.queryForObject(
                "SELECT read_at FROM notification WHERE id = ?", LocalDateTime.class, notificationId);
    }

    private MvcTestResult get(Cookie session, String uri) {
        return mvc.get().uri(uri).cookie(session).exchange();
    }

    private MvcTestResult post(Cookie session, String uri) {
        return mvc.post().uri(uri).cookie(session).with(csrf()).exchange();
    }

    private static List<Long> ids(MvcTestResult result) {
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<Number> ids = JsonPath.read(body, "$.content[*].notificationId");
        return ids.stream().map(Number::longValue).toList();
    }

    private Cookie login(Member member) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), PASSWORD);
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }
}
