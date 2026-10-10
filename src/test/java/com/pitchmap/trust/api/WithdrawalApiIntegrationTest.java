package com.pitchmap.trust.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.application.CiRetentionCleanupService;
import com.pitchmap.trust.application.CompanionReviewFixture;
import com.pitchmap.trust.application.SanctionConfirmCommand;
import com.pitchmap.trust.application.SanctionConfirmService;
import com.pitchmap.trust.domain.SanctionType;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
class WithdrawalApiIntegrationTest {

    private static final String ME_PATH = "/api/me";
    private static final String PASSWORD = "Valid-pass1";
    private static final String WRONG_PASSWORD = "Wrong-pass1!";
    private static final String SESSION_COOKIE = "SESSION";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private CiRetentionCleanupService ciRetentionCleanupService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private CompanionReviewFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-01][PV-01][PV-03] 탈퇴하면 204이고 이메일·비밀번호 해시·자기 신고 연령대와 성별이 NULL이 되며 상태는 WITHDRAWN이다")
    void withdrawalErasesAccountData() {
        Member member = saveActiveMember();
        jdbc.update(
                "UPDATE member SET self_age_group = 'TWENTIES', self_gender = 'FEMALE' WHERE id = ?", member.getId());
        Cookie session = login(member);

        MvcTestResult result = withdraw(session, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT email, password_hash, self_age_group, self_gender, status, withdrawn_at FROM member WHERE id = ?",
                member.getId());
        assertThat(row.get("email")).isNull();
        assertThat(row.get("password_hash")).isNull();
        assertThat(row.get("self_age_group")).isNull();
        assertThat(row.get("self_gender")).isNull();
        assertThat(row.get("status")).isEqualTo("WITHDRAWN");
        assertThat(row.get("withdrawn_at")).isEqualTo(utc(clock.instant().toEpochMilli()));
    }

    @Test
    @DisplayName("[F-01][PV-02] 탈퇴하면 닉네임이 탈퇴회원_{id}로 바뀌고, 기존 닉네임은 다른 회원이 쓸 수 있다")
    void withdrawalAnonymizesNickname() {
        Member member = saveActiveMember();
        String oldNickname = member.getNickname();
        Cookie session = login(member);

        withdraw(session, PASSWORD);

        assertThat(column(member, "nickname")).isEqualTo("탈퇴회원_" + member.getId());
        assertThat(mvc.get().uri("/api/members/" + member.getId() + "/profile").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        Member another = memberRepository.save(aMember().nickname(oldNickname).build());
        assertThat(another.getNickname()).isEqualTo(oldNickname);
    }

    @Test
    @DisplayName("[F-01][PV-01] 탈퇴한 뒤 예전 이메일과 비밀번호로 로그인하면 401 LOGIN_FAILED이고, 같은 이메일로 다시 가입할 수 있다")
    void oldCredentialsFailAndEmailCanBeReused() {
        Member member = saveActiveMember();
        String email = member.getEmail();
        withdraw(login(member), PASSWORD);

        MvcTestResult loginAgain = postJson("/api/auth/login", loginBody(email, PASSWORD), null);
        MvcTestResult signup = postJson(
                "/api/members",
                "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\"}"
                        .formatted(email, PASSWORD, TestSequence.nickname()),
                null);

        assertThat(loginAgain).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(loginAgain).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
        assertThat(signup).hasStatus2xxSuccessful();
        assertThat(count("member WHERE email = ?", email)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-01][PV-02] 가입이나 닉네임 변경에서 탈퇴회원_숫자 닉네임은 400 INVALID_INPUT이다")
    void reservedNicknameIsRejected() {
        Member member = saveActiveMember();
        Cookie session = login(member);

        MvcTestResult signup = postJson(
                "/api/members",
                "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"탈퇴회원_1\"}"
                        .formatted(TestSequence.email(), PASSWORD),
                null);
        MvcTestResult rename = mvc.patch()
                .uri(ME_PATH)
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"탈퇴회원_99\"}")
                .exchange();

        assertThat(signup).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(signup).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(rename).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(rename).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-11][PV-04][PV-05] 제재 이력이 없는 회원이 탈퇴하면 출생연도·성별은 NULL이고 CI 해시는 30일 뒤까지 남는다")
    void identityIsErasedAndCiRetainedForThirtyDays() {
        Member member = saveActiveMember();
        Cookie session = login(member);
        verifyIdentity(session, "key-" + TestSequence.next());
        String ciHash = identityColumn(member, "ci_hash");

        withdraw(session, PASSWORD);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT birth_year, gender, ci_hash, ci_retained_until FROM identity_verification WHERE member_id = ?",
                member.getId());
        assertThat(row.get("birth_year")).isNull();
        assertThat(row.get("gender")).isNull();
        assertThat(row.get("ci_hash")).isEqualTo(ciHash);
        assertThat(row.get("ci_retained_until"))
                .isEqualTo(LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(30)), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("[F-16][PV-05] 해제되지 않은 제재 이력이 있으면 CI 해시를 365일 뒤까지 남기고, 해제된 제재만 있으면 30일이다")
    void ciRetentionDependsOnUnliftedSanction() {
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        Member sanctioned = saveActiveMember();
        Member lifted = saveActiveMember();
        Cookie sanctionedSession = login(sanctioned);
        Cookie liftedSession = login(lifted);
        verifyIdentity(sanctionedSession, "key-" + TestSequence.next());
        verifyIdentity(liftedSession, "key-" + TestSequence.next());
        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(sanctioned.getId(), null, SanctionType.WARNING, "욕설", adminId));
        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(lifted.getId(), null, SanctionType.WARNING, "욕설", adminId));
        jdbc.update("UPDATE sanction SET status = 'LIFTED', lifted_at = NOW(6) WHERE member_id = ?", lifted.getId());

        withdraw(sanctionedSession, PASSWORD);
        withdraw(liftedSession, PASSWORD);

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        assertThat(retainedUntil(sanctioned)).isEqualTo(now.plusDays(365));
        assertThat(retainedUntil(lifted)).isEqualTo(now.plusDays(30));
    }

    @Test
    @DisplayName("[F-11][PV-05] CI 해시가 남아 있는 동안 같은 사람이 다른 계정으로 본인확인하면 409 IDENTITY_CI_DUPLICATED이다")
    void sameIdentityCannotVerifyWhileCiIsRetained() {
        Member first = saveActiveMember();
        String demoKey = "same-person-" + TestSequence.next();
        Cookie firstSession = login(first);
        verifyIdentity(firstSession, demoKey);
        withdraw(firstSession, PASSWORD);
        Member second = saveActiveMember();
        Cookie secondSession = login(second);

        MvcTestResult result = identityRequest(secondSession, demoKey);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("IDENTITY_CI_DUPLICATED");
    }

    @Test
    @DisplayName("[F-11][PV-05] CI 보관 기한이 지나 정리 작업이 행을 지우면 같은 사람이 다른 계정으로 본인확인할 수 있다")
    void sameIdentityCanVerifyAfterCiRetentionCleanup() {
        Member first = saveActiveMember();
        String demoKey = "same-person-" + TestSequence.next();
        Cookie firstSession = login(first);
        verifyIdentity(firstSession, demoKey);
        withdraw(firstSession, PASSWORD);
        Member second = saveActiveMember();
        Cookie secondSession = login(second);
        clock.advance(Duration.ofDays(30).plusSeconds(1));

        int deleted = ciRetentionCleanupService.deleteRetentionExpired();
        MvcTestResult result = identityRequest(secondSession, demoKey);

        assertThat(deleted).isEqualTo(1);
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[PV-06][PV-07][PV-08][PV-09] 로그인 기록, 신고·제재, 장소 후기, 동행 후기, 박지는 탈퇴해도 그대로 남는다")
    void otherRecordsRemain() {
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        Member member = saveActiveMember();
        Cookie session = login(member);
        long basecampId = fixture.saveBasecamp("COMPLETED", clock.instant().minus(Duration.ofDays(30)));
        long reporterId = fixture.leaderIdOf(basecampId);
        fixture.insertMember(basecampId, member.getId(), "MEMBER", "ACTIVE");
        jdbc.update(
                "INSERT INTO member_report (reporter_id, target_member_id, basecamp_id, kind, type, content, urgent,"
                        + " status, created_at, updated_at) VALUES (?, ?, ?, 'MEMBER', 'NO_SHOW', '약속 장소에 나오지 않았어요',"
                        + " FALSE, 'RECEIVED', NOW(6), NOW(6))",
                reporterId,
                member.getId(),
                basecampId);
        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(member.getId(), null, SanctionType.WARNING, "욕설", adminId));
        long spotId = insertBakji(member.getId());
        insertSpotReview(spotId, member.getId());
        fixture.insertReview(basecampId, member.getId(), reporterId, true, clock.instant());
        int loginHistoryBefore = count("login_history WHERE member_id = ?", member.getId());
        int throttleBefore = count("password_reset_throttle");

        withdraw(session, PASSWORD);

        assertThat(loginHistoryBefore).isPositive();
        assertThat(count("login_history WHERE member_id = ?", member.getId())).isEqualTo(loginHistoryBefore);
        assertThat(count("member_report WHERE target_member_id = ? AND content = '약속 장소에 나오지 않았어요'", member.getId()))
                .isEqualTo(1);
        assertThat(count("sanction WHERE member_id = ? AND reason = '욕설' AND status = 'ACTIVE'", member.getId()))
                .isEqualTo(1);
        assertThat(count("spot_review WHERE member_id = ?", member.getId())).isEqualTo(1);
        assertThat(count("companion_review WHERE reviewer_id = ?", member.getId()))
                .isEqualTo(1);
        assertThat(count("bakji_detail WHERE reporter_id = ?", member.getId())).isEqualTo(1);
        assertThat(count("password_reset_throttle")).isEqualTo(throttleBefore);
    }

    @Test
    @DisplayName("[F-01] 탈퇴한 회원의 닉네임은 장소 후기 목록, 박지 상세, 받은 동행 후기, 베이스캠프 상세에서 탈퇴회원_{id}로 보인다")
    void withdrawnNicknameShowsInPublicViews() {
        Member member = saveActiveMember();
        Member reviewee = saveActiveMember();
        Cookie session = login(member);
        Cookie revieweeSession = login(reviewee);
        long spotId = insertBakji(member.getId());
        insertSpotReview(spotId, member.getId());
        long completedBasecamp =
                fixture.saveBasecamp("COMPLETED", clock.instant().minus(Duration.ofDays(30)));
        fixture.insertMember(completedBasecamp, member.getId(), "MEMBER", "ACTIVE");
        fixture.insertMember(completedBasecamp, reviewee.getId(), "MEMBER", "ACTIVE");
        fixture.insertReview(completedBasecamp, member.getId(), reviewee.getId(), true, clock.instant());
        long openBasecamp = fixture.saveBasecamp("RECRUITING", null);
        fixture.insertMember(openBasecamp, member.getId(), "MEMBER", "ACTIVE");
        String anonymized = "탈퇴회원_" + member.getId();

        withdraw(session, PASSWORD);

        MvcTestResult reviews =
                mvc.get().uri("/api/spots/" + spotId + "/reviews").exchange();
        MvcTestResult spot = mvc.get().uri("/api/spots/" + spotId).exchange();
        MvcTestResult received = mvc.get()
                .uri("/api/me/companion-reviews/received")
                .cookie(revieweeSession)
                .exchange();
        MvcTestResult basecamp = mvc.get().uri("/api/basecamps/" + openBasecamp).exchange();
        assertThat(reviews).hasStatus(HttpStatus.OK);
        assertThat(reviews)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo(anonymized);
        assertThat(spot).bodyJson().extractingPath("$.bakji.reporter.nickname").isEqualTo(anonymized);
        assertThat(received).hasStatus(HttpStatus.OK);
        assertThat(received)
                .bodyJson()
                .extractingPath("$.content[0].reviewer.nickname")
                .isEqualTo(anonymized);
        assertThat(basecamp).hasStatus(HttpStatus.OK);
        assertThat(basecamp)
                .bodyJson()
                .extractingPath("$.members[?(@.memberId == %d)].nickname".formatted(member.getId()))
                .asList()
                .containsExactly(anonymized);
    }

    @Test
    @DisplayName("[F-01][PW-02] 탈퇴하면 기기가 여러 대여도 그 회원의 세션이 모두 지워져 옛 쿠키는 401이고, 응답이 SESSION 쿠키를 만료시킨다")
    void allSessionsAreRemoved() {
        Member member = saveActiveMember();
        Member other = saveActiveMember();
        Cookie first = login(member);
        Cookie second = login(member);
        Cookie otherSession = login(other);
        assertThat(sessionCount(member)).isEqualTo(2);

        MvcTestResult result = withdraw(first, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        Cookie expired = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(expired).isNotNull();
        assertThat(expired.getMaxAge()).isZero();
        assertThat(sessionCount(member)).isZero();
        assertThat(mvc.get().uri(ME_PATH).cookie(first).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri(ME_PATH).cookie(second).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(sessionCount(other)).isEqualTo(1);
        assertThat(mvc.get().uri(ME_PATH).cookie(otherSession).exchange()).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-01][PW-03] 비밀번호가 틀리면 401 LOGIN_FAILED이고 회원 정보와 세션은 그대로이며 로그인 실패로 세지 않는다")
    void wrongPasswordChangesNothing() {
        Member member = saveActiveMember();
        Cookie session = login(member);
        int loginHistoryBefore = count("login_history WHERE member_id = ?", member.getId());

        for (int attempt = 0; attempt < 6; attempt++) {
            MvcTestResult result = withdraw(session, WRONG_PASSWORD);

            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("LOGIN_FAILED");
        }

        assertThat(column(member, "status")).isEqualTo("ACTIVE");
        assertThat(column(member, "email")).isEqualTo(member.getEmail());
        assertThat(column(member, "nickname")).isEqualTo(member.getNickname());
        assertThat(sessionCount(member)).isEqualTo(1);
        assertThat(count("login_history WHERE member_id = ?", member.getId())).isEqualTo(loginHistoryBefore);
        assertThat(count("outbox_event WHERE event_type LIKE 'WITHDRAWAL%'")).isZero();
        MvcTestResult loginAgain = postJson("/api/auth/login", loginBody(member.getEmail(), PASSWORD), null);
        assertThat(loginAgain).hasStatus(HttpStatus.OK);
    }

    @Test
    @DisplayName("[F-01][TR-03] 이메일 인증 전인 회원도 탈퇴할 수 있다")
    void unverifiedMemberCanWithdraw() {
        Member member = memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .build());
        Cookie session = login(member);
        assertThat(column(member, "status")).isEqualTo("UNVERIFIED");

        MvcTestResult result = withdraw(session, PASSWORD);

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(column(member, "status")).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("[F-01] 로그인하지 않았거나 비밀번호가 비었거나 CSRF 토큰이 없으면 탈퇴하지 못한다")
    void rejectsInvalidRequests() {
        Member member = saveActiveMember();
        Cookie session = login(member);

        MvcTestResult anonymous = withdraw(null, PASSWORD);
        MvcTestResult blank = withdraw(session, "");
        MvcTestResult noCsrf = mvc.delete()
                .uri(ME_PATH)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"%s\"}".formatted(PASSWORD))
                .exchange();

        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(blank).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(blank).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(noCsrf).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(column(member, "status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-12][F-19][PV-02] 탈퇴하면 베이스캠프 정리와 행사 신청 정리 이벤트가 회원마다 하나씩 기록된다")
    void recordsCleanupEvents() {
        Member member = saveActiveMember();

        withdraw(login(member), PASSWORD);

        assertThat(eventPayloads("WITHDRAWAL_BASECAMP_CLEANUP", member.getId())).hasSize(1);
        assertThat(eventPayloads("WITHDRAWAL_PROGRAM_CLEANUP", member.getId())).hasSize(1);
        assertThat(count("outbox_event WHERE payload LIKE ?", "%" + member.getEmail() + "%"))
                .isZero();
    }

    private List<String> eventPayloads(String eventType, long memberId) {
        return jdbc.queryForList(
                "SELECT payload FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                String.class,
                eventType,
                memberId);
    }

    private Member saveActiveMember() {
        Member member = memberRepository.save(aMember()
                .email(TestSequence.email())
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .build());
        jdbc.update("UPDATE member SET status = 'ACTIVE', email_verified_at = NOW(6) WHERE id = ?", member.getId());
        return memberRepository.findById(member.getId()).orElseThrow();
    }

    private Cookie login(Member member) {
        MvcTestResult result = postJson("/api/auth/login", loginBody(member.getEmail(), PASSWORD), null);
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private MvcTestResult withdraw(Cookie session, String password) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.delete().uri(ME_PATH).with(csrf());
        if (session != null) {
            request.cookie(session);
        }
        return request.contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"%s\"}".formatted(password))
                .exchange();
    }

    private MvcTestResult postJson(String path, String body, Cookie session) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.post().uri(path).with(csrf());
        if (session != null) {
            request.cookie(session);
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private void verifyIdentity(Cookie session, String demoIdentityKey) {
        assertThat(identityRequest(session, demoIdentityKey)).hasStatus(HttpStatus.OK);
    }

    private MvcTestResult identityRequest(Cookie session, String demoIdentityKey) {
        return postJson(
                "/api/me/identity-verification",
                "{\"birthYear\":1995,\"gender\":\"FEMALE\",\"demoIdentityKey\":\"%s\"}".formatted(demoIdentityKey),
                session);
    }

    private static String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private long insertBakji(long reporterId) {
        String name = TestSequence.unique("박지");
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_SRID(POINT(128.75, 37.71), 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                name);
        long spotId = jdbc.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
        jdbc.update(
                "INSERT INTO bakji_detail (spot_id, reporter_id, has_water, has_toilet, created_at, updated_at)"
                        + " VALUES (?, ?, TRUE, FALSE, NOW(6), NOW(6))",
                spotId,
                reporterId);
        return spotId;
    }

    private void insertSpotReview(long spotId, long memberId) {
        jdbc.update(
                "INSERT INTO spot_review (spot_id, member_id, visited_date, rating, content, created_at, updated_at)"
                        + " VALUES (?, ?, '2026-09-20', 5, '조용하고 좋았어요', NOW(6), NOW(6))",
                spotId,
                memberId);
    }

    private LocalDateTime retainedUntil(Member member) {
        return jdbc.queryForObject(
                "SELECT ci_retained_until FROM identity_verification WHERE member_id = ?",
                LocalDateTime.class,
                member.getId());
    }

    private String identityColumn(Member member, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM identity_verification WHERE member_id = ?", String.class, member.getId());
    }

    private String column(Member member, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM member WHERE id = ?", String.class, member.getId());
    }

    private int sessionCount(Member member) {
        return count("SPRING_SESSION WHERE PRINCIPAL_NAME = ?", String.valueOf(member.getId()));
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }

    private static LocalDateTime utc(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC);
    }
}
