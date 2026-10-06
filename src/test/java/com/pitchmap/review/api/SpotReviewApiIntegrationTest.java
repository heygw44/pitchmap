package com.pitchmap.review.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.ParkAreaJudgement;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.infra.SpotJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicInteger;
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
class SpotReviewApiIntegrationTest {

    private static final String VALID_PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-10] 인증 회원이 CSRF 토큰과 함께 후기를 쓰면 201이고, 비로그인 사용자의 목록과 상세에 그 후기가 보인다")
    void writeThenListAndDetailAsAnonymous() {
        // given
        long spotId = saveSpot();
        Member author = saveMember("새벽능선");
        Cookie session = verifiedSession(author);

        // when
        MvcTestResult created = send(
                mvc.post().uri("/api/spots/" + spotId + "/reviews"),
                session,
                "{\"visitedDate\":\"2026-10-04\",\"rating\":4,\"content\":\"물이 가까웠다\"}");
        MvcTestResult list = mvc.get().uri("/api/spots/" + spotId + "/reviews").exchange();
        MvcTestResult detail = mvc.get().uri("/api/spots/" + spotId).exchange();

        // then
        assertThat(created).hasStatus(HttpStatus.CREATED);
        int reviewId = reviewIdOf(created);
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(list).bodyJson().extractingPath("$.content[0].reviewId").isEqualTo(reviewId);
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0].author.memberId")
                .isEqualTo((int) (long) author.getId());
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo("새벽능선");
        assertThat(list).bodyJson().extractingPath("$.content[0].visitedDate").isEqualTo("2026-10-04");
        assertThat(list).bodyJson().extractingPath("$.content[0].rating").isEqualTo(4);
        assertThat(list).bodyJson().extractingPath("$.content[0].content").isEqualTo("물이 가까웠다");
        assertThat(list).bodyJson().extractingPath("$.content[0].createdAt").isEqualTo("2026-10-05T03:00:00Z");
        assertThat(list).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail).bodyJson().extractingPath("$.rating.count").isEqualTo(1);
        assertThat(detail).bodyJson().extractingPath("$.rating.average").isEqualTo(4.0);
        assertThat(detail)
                .bodyJson()
                .extractingPath("$.recentReviews[0].reviewId")
                .isEqualTo(reviewId);
    }

    @Test
    @DisplayName("[F-10] 응답에는 작성자의 이메일이 없다")
    void responsesDoNotExposeEmail() throws Exception {
        // given
        long spotId = saveSpot();
        Member author = saveMember("새벽능선");
        send(
                mvc.post().uri("/api/spots/" + spotId + "/reviews"),
                verifiedSession(author),
                "{\"visitedDate\":\"2026-10-04\",\"rating\":4,\"content\":\"좋다\"}");

        // when
        MvcTestResult list = mvc.get().uri("/api/spots/" + spotId + "/reviews").exchange();
        MvcTestResult detail = mvc.get().uri("/api/spots/" + spotId).exchange();

        // then
        assertThat(list.getResponse().getContentAsString()).doesNotContain(author.getEmail());
        assertThat(detail.getResponse().getContentAsString()).doesNotContain(author.getEmail());
    }

    @Test
    @DisplayName("[F-10] 같은 방문일로 다시 쓰면 409 SPOT_REVIEW_DUPLICATED, 미래 방문일은 400 INVALID_INPUT이다")
    void duplicatedAndFutureDateErrors() {
        // given
        long spotId = saveSpot();
        Cookie session = verifiedSession(saveMember(null));
        String uri = "/api/spots/" + spotId + "/reviews";
        send(mvc.post().uri(uri), session, "{\"visitedDate\":\"2026-10-04\",\"rating\":4,\"content\":\"좋다\"}");

        // when
        MvcTestResult duplicated =
                send(mvc.post().uri(uri), session, "{\"visitedDate\":\"2026-10-04\",\"rating\":5,\"content\":\"또\"}");
        MvcTestResult future =
                send(mvc.post().uri(uri), session, "{\"visitedDate\":\"2026-10-06\",\"rating\":5,\"content\":\"내일\"}");

        // then
        assertThat(duplicated).hasStatus(HttpStatus.CONFLICT);
        assertThat(duplicated).bodyJson().extractingPath("$.code").isEqualTo("SPOT_REVIEW_DUPLICATED");
        assertThat(future).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(future).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(count(spotId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-10] 작성자는 PATCH로 고친 항목을 받고 DELETE로 지우며, 다른 회원은 403 ACCESS_DENIED를 받는다")
    void patchAndDeleteByAuthorAndOthers() {
        // given
        long spotId = saveSpot();
        Cookie authorSession = verifiedSession(saveMember(null));
        Cookie otherSession = verifiedSession(saveMember(null));
        MvcTestResult created = send(
                mvc.post().uri("/api/spots/" + spotId + "/reviews"),
                authorSession,
                "{\"visitedDate\":\"2026-10-04\",\"rating\":2,\"content\":\"별로\"}");
        int reviewId = reviewIdOf(created);
        String uri = "/api/reviews/" + reviewId;

        // when
        MvcTestResult othersPatch = send(mvc.patch().uri(uri), otherSession, "{\"rating\":1,\"content\":\"나쁘다\"}");
        MvcTestResult othersDelete = send(mvc.delete().uri(uri), otherSession, null);
        MvcTestResult patched = send(mvc.patch().uri(uri), authorSession, "{\"rating\":5,\"content\":\"다시 가니 좋다\"}");
        MvcTestResult deleted = send(mvc.delete().uri(uri), authorSession, null);

        // then
        assertThat(othersPatch).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(othersPatch).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(othersDelete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patched).hasStatus(HttpStatus.OK);
        assertThat(patched).bodyJson().extractingPath("$.reviewId").isEqualTo(reviewId);
        assertThat(patched).bodyJson().extractingPath("$.rating").isEqualTo(5);
        assertThat(patched).bodyJson().extractingPath("$.content").isEqualTo("다시 가니 좋다");
        assertThat(patched).bodyJson().extractingPath("$.visitedDate").isEqualTo("2026-10-04");
        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(count(spotId)).isZero();
    }

    @Test
    @DisplayName("[F-10] 로그인하지 않으면 쓰기는 401이고, 이메일 인증 전 회원은 403 MEMBER_NOT_VERIFIED이며, 목록은 로그인 없이 읽는다")
    void authorizationLevels() {
        // given
        long spotId = saveSpot();
        String uri = "/api/spots/" + spotId + "/reviews";
        String body = "{\"visitedDate\":\"2026-10-04\",\"rating\":4,\"content\":\"좋다\"}";

        // when
        MvcTestResult anonymous = mvc.post()
                .uri(uri)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        MvcTestResult unverified = send(mvc.post().uri(uri), login(saveMember(null)), body);
        MvcTestResult list = mvc.get().uri(uri).exchange();

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(count(spotId)).isZero();
    }

    @Test
    @DisplayName("[F-10] 숨긴 장소의 후기 목록과 작성은 404 NOT_FOUND다")
    void hiddenSpotIsNotFound() {
        // given
        long spotId = saveSpot();
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", spotId);
        Cookie session = verifiedSession(saveMember(null));

        // when
        MvcTestResult list = mvc.get().uri("/api/spots/" + spotId + "/reviews").exchange();
        MvcTestResult write = send(
                mvc.post().uri("/api/spots/" + spotId + "/reviews"),
                session,
                "{\"visitedDate\":\"2026-10-04\",\"rating\":4,\"content\":\"좋다\"}");

        // then
        assertThat(list).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(write).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder builder, Cookie session, String body) {
        MockMvcTester.MockMvcRequestBuilder request = builder.with(csrf()).cookie(session);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static int reviewIdOf(MvcTestResult created) {
        AtomicInteger reviewId = new AtomicInteger();
        assertThat(created)
                .bodyJson()
                .extractingPath("$.reviewId")
                .asNumber()
                .satisfies(number -> reviewId.set(number.intValue()));
        return reviewId.get();
    }

    private int count(long spotId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM spot_review WHERE spot_id = ?", Integer.class, spotId);
    }

    private long saveSpot() {
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private Member saveMember(String nickname) {
        String passwordHash = passwordEncoder.encode(VALID_PASSWORD);
        Member member = nickname == null
                ? aMember().passwordHash(passwordHash).build()
                : aMember().passwordHash(passwordHash).nickname(nickname).build();
        return memberRepository.saveAndFlush(member);
    }

    private Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), VALID_PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private Cookie verifiedSession(Member member) {
        Cookie session = login(member);
        String code = emailVerificationService
                .issueFor(member.getId(), LOCAL_IP)
                .orElseThrow()
                .code();
        MvcTestResult verified = mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))
                .exchange();
        assertThat(verified).hasStatus(HttpStatus.OK);
        return session;
    }
}
