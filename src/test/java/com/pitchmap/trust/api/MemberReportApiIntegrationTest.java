package com.pitchmap.trust.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.application.CompanionReviewFixture;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class MemberReportApiIntegrationTest {

    private static final String PATH = "/api/member-reports";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String LOCAL_IP = "127.0.0.1";
    private static final String NOT_ELIGIBLE = "REPORT_NOT_ELIGIBLE";
    private static final String DUPLICATED = "REPORT_DUPLICATED";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

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
    @DisplayName("[SN-02] 승인 대기 신청자가 멤버를 신고하면 201과 reportId, RECEIVED를 응답하고 임시 정지는 없다")
    void applicantReportsMember() {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        long member = joinedMember(basecampId);
        long applicant = applicant(basecampId, "PENDING");
        Cookie session = verifiedSession(applicant);

        MvcTestResult result = post(session, memberReport(member, basecampId, "NO_SHOW"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("RECEIVED");
        long reportId = ((Number) bodyJson(result, "$.reportId")).longValue();
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(reporter_id, ':', target_member_id, ':', kind, ':', status, ':', urgent)"
                                + " FROM member_report WHERE id = ?",
                        String.class,
                        reportId))
                .isEqualTo(applicant + ":" + member + ":MEMBER:RECEIVED:0");
        assertThat(count("sanction")).isZero();
        assertThat(memberStatus(member)).isEqualTo("UNVERIFIED");
    }

    @Test
    @DisplayName("[SN-02] 멤버가 캠프 리더를, 캠프 리더가 멤버를 신고하면 모두 201이다")
    void memberAndLeaderReportEachOther() {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        long leader = fixture.leaderIdOf(basecampId);
        long member = joinedMember(basecampId);

        MvcTestResult memberToLeader =
                post(verifiedSession(member), memberReport(leader, basecampId, "OFFENSIVE_BEHAVIOR"));
        MvcTestResult leaderToMember =
                post(verifiedSession(leader), memberReport(member, basecampId, "OFFENSIVE_BEHAVIOR"));

        assertThat(memberToLeader).hasStatus(HttpStatus.CREATED);
        assertThat(leaderToMember).hasStatus(HttpStatus.CREATED);
        assertThat(count("member_report")).isEqualTo(2);
    }

    @Test
    @DisplayName("[SN-02] 거절·취소된 신청자와 탈퇴한 멤버도 이력이 있으면 신고할 수 있다")
    void historyOfAnyStatusIsEnough() {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        long member = joinedMember(basecampId);
        long rejected = applicant(basecampId, "REJECTED");
        long leaver = fixture.saveVerifiedMember(TestSequence.nickname());
        fixture.insertMember(basecampId, leaver, "MEMBER", "LEFT");

        MvcTestResult byRejected = post(verifiedSession(rejected), memberReport(member, basecampId, "NO_SHOW"));
        MvcTestResult onLeaver = post(verifiedSession(member), memberReport(leaver, basecampId, "NO_SHOW"));

        assertThat(byRejected).hasStatus(HttpStatus.CREATED);
        assertThat(onLeaver).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[SN-02] 베이스캠프와 무관한 신고자, 무관한 대상, 자기 자신, 없는 베이스캠프는 403 REPORT_NOT_ELIGIBLE이다")
    void rejectsIneligibleReports() {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        long member = joinedMember(basecampId);
        long stranger = fixture.saveVerifiedMember(TestSequence.nickname());
        Cookie memberSession = verifiedSession(member);

        MvcTestResult unrelatedReporter = post(verifiedSession(stranger), memberReport(member, basecampId, "NO_SHOW"));
        MvcTestResult unrelatedTarget = post(memberSession, memberReport(stranger, basecampId, "NO_SHOW"));
        MvcTestResult self = post(memberSession, memberReport(member, basecampId, "NO_SHOW"));
        MvcTestResult noBasecamp =
                post(memberSession, memberReport(fixture.leaderIdOf(basecampId), 999_999L, "NO_SHOW"));

        for (MvcTestResult result : new MvcTestResult[] {unrelatedReporter, unrelatedTarget, self, noBasecamp}) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(NOT_ELIGIBLE);
        }
        assertThat(count("member_report")).isZero();
    }

    @Test
    @DisplayName("[SN-03] 같은 베이스캠프에서 같은 회원을 같은 종류로 다시 신고하면 409 REPORT_DUPLICATED이고, 유형이 달라도 같은 종류면 중복이다")
    void duplicateReportIsConflict() {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        long leader = fixture.leaderIdOf(basecampId);
        Cookie session = verifiedSession(joinedMember(basecampId));

        MvcTestResult first = post(session, memberReport(leader, basecampId, "NO_SHOW"));
        MvcTestResult second = post(session, memberReport(leader, basecampId, "NO_SHOW"));
        MvcTestResult otherType = post(session, memberReport(leader, basecampId, "MONEY_REQUEST"));

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CONFLICT);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo(DUPLICATED);
        assertThat(otherType).hasStatus(HttpStatus.CONFLICT);
        assertThat(count("member_report")).isEqualTo(1);
    }

    @Test
    @DisplayName("[SN-03] 같은 상대와 같은 베이스캠프라도 종류가 다르면 따로 신고할 수 있다")
    void differentKindIsNotDuplicate() {
        CompletedScenario scenario = completedScenario(Duration.ofDays(20));
        long reviewId = fixture.insertReview(
                scenario.basecampId, scenario.leaderId, scenario.reporterId, true, clock.instant());
        Cookie session = verifiedSession(scenario.reporterId);

        MvcTestResult memberKind = post(session, memberReport(scenario.leaderId, scenario.basecampId, "NO_SHOW"));
        MvcTestResult reviewKind = post(session, reviewReport(scenario.leaderId, scenario.basecampId, reviewId));

        assertThat(memberKind).hasStatus(HttpStatus.CREATED);
        assertThat(reviewKind).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[SN-07] 내가 받은 공개된 후기를 신고하면 201이고 두 번째는 409 REPORT_DUPLICATED다")
    void reportsReceivedRevealedReview() {
        CompletedScenario scenario = completedScenario(Duration.ofDays(20));
        long reviewId = fixture.insertReview(
                scenario.basecampId, scenario.leaderId, scenario.reporterId, false, clock.instant());
        Cookie session = verifiedSession(scenario.reporterId);

        MvcTestResult first = post(session, reviewReport(scenario.leaderId, scenario.basecampId, reviewId));
        MvcTestResult second = post(session, reviewReport(scenario.leaderId, scenario.basecampId, reviewId));

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(kind, ':', type, ':', companion_review_id) FROM member_report", String.class))
                .isEqualTo("REVIEW:INAPPROPRIATE_REVIEW:" + reviewId);
        assertThat(second).hasStatus(HttpStatus.CONFLICT);
        assertThat(second).bodyJson().extractingPath("$.code").isEqualTo(DUPLICATED);
    }

    @Test
    @DisplayName("[SN-07] 작성 기한이 남아 있어도 내가 상대에게 후기를 썼다면 공개된 후기라서 신고할 수 있다")
    void reviewRevealedByReverseReviewIsReportable() {
        CompletedScenario scenario = completedScenario(Duration.ZERO);
        long reviewId = fixture.insertReview(
                scenario.basecampId, scenario.leaderId, scenario.reporterId, true, clock.instant());
        fixture.insertReview(scenario.basecampId, scenario.reporterId, scenario.leaderId, true, clock.instant());

        MvcTestResult result = post(
                verifiedSession(scenario.reporterId), reviewReport(scenario.leaderId, scenario.basecampId, reviewId));

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[SN-07] 남의 후기, 다른 베이스캠프의 후기, 숨긴 후기, 블라인드 공개 전 후기는 403 REPORT_NOT_ELIGIBLE이다")
    void rejectsIneligibleReviews() {
        CompletedScenario scenario = completedScenario(Duration.ofDays(20));
        long thirdParty = joinedMember(scenario.basecampId);
        long otherCampId = fixture.saveBasecamp("COMPLETED", clock.instant().minus(Duration.ofDays(20)));
        Cookie session = verifiedSession(scenario.reporterId);
        long othersReview =
                fixture.insertReview(scenario.basecampId, scenario.leaderId, thirdParty, true, clock.instant());
        long writtenByOther =
                fixture.insertReview(scenario.basecampId, thirdParty, scenario.reporterId, true, clock.instant());
        long hidden = fixture.insertReview(
                scenario.basecampId, scenario.leaderId, scenario.reporterId, true, clock.instant());
        fixture.hide(hidden);
        long sendToOtherCamp = fixture.insertReview(
                otherCampId, fixture.leaderIdOf(otherCampId), scenario.reporterId, true, clock.instant());
        CompletedScenario blind = completedScenario(Duration.ZERO);
        long notRevealed =
                fixture.insertReview(blind.basecampId, blind.leaderId, blind.reporterId, true, clock.instant());

        MvcTestResult othersResult = post(session, reviewReport(scenario.leaderId, scenario.basecampId, othersReview));
        MvcTestResult otherAuthor = post(session, reviewReport(scenario.leaderId, scenario.basecampId, writtenByOther));
        MvcTestResult hiddenResult = post(session, reviewReport(scenario.leaderId, scenario.basecampId, hidden));
        MvcTestResult wrongBasecamp =
                post(session, reviewReport(scenario.leaderId, scenario.basecampId, sendToOtherCamp));
        MvcTestResult missing = post(session, reviewReport(scenario.leaderId, scenario.basecampId, 999_999L));
        MvcTestResult blindResult =
                post(verifiedSession(blind.reporterId), reviewReport(blind.leaderId, blind.basecampId, notRevealed));

        for (MvcTestResult result :
                new MvcTestResult[] {othersResult, otherAuthor, hiddenResult, wrongBasecamp, missing, blindResult}) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(NOT_ELIGIBLE);
        }
        assertThat(count("member_report")).isZero();
    }

    @Test
    @DisplayName("[F-16] 종류와 유형이 맞지 않거나 후기 ID가 종류와 맞지 않으면 400 INVALID_INPUT이다")
    void rejectsInconsistentKind() {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        long leader = fixture.leaderIdOf(basecampId);
        Cookie session = verifiedSession(joinedMember(basecampId));

        String reviewTypeAsMember = body(leader, basecampId, "MEMBER", null, "INAPPROPRIATE_REVIEW");
        String memberTypeAsReview = body(leader, basecampId, "REVIEW", 1L, "NO_SHOW");
        String reviewWithoutId = body(leader, basecampId, "REVIEW", null, "INAPPROPRIATE_REVIEW");
        String memberWithId = body(leader, basecampId, "MEMBER", 1L, "NO_SHOW");

        for (String body : new String[] {reviewTypeAsMember, memberTypeAsReview, reviewWithoutId, memberWithId}) {
            MvcTestResult result = post(session, body);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
    }

    @Test
    @DisplayName("[TR-03] 이메일 인증 전 회원은 403 MEMBER_NOT_VERIFIED이고, 로그인하지 않으면 401 AUTHENTICATION_REQUIRED다")
    void requiresVerifiedMember() {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        long member = joinedMember(basecampId);
        String body = memberReport(fixture.leaderIdOf(basecampId), basecampId, "NO_SHOW");

        MvcTestResult unverified = post(login(member), body);
        MvcTestResult anonymous = post(null, body);

        assertThat(unverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[SN-05][SN-12] 성희롱·위협 신고는 대상을 72시간 임시 정지하고 기록을 남기며, 대상의 세션만 모두 지운다")
    void harassmentReportSuspendsTargetAndInvalidatesItsSessions() {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        long target = joinedMember(basecampId);
        long reporter = joinedMember(basecampId);
        Cookie reporterSession = verifiedSession(reporter);
        Cookie targetFirst = login(target);
        Cookie targetSecond = login(target);
        assertThat(sessionCount(target)).isEqualTo(2);

        MvcTestResult result = post(reporterSession, memberReport(target, basecampId, "HARASSMENT_OR_THREAT"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        long reportId = ((Number) bodyJson(result, "$.reportId")).longValue();
        Instant until = clock.instant().plus(Duration.ofHours(72));
        Member suspended = memberRepository.findById(target).orElseThrow();
        assertThat(suspended.getStatus().name()).isEqualTo("SUSPENDED");
        assertThat(suspended.getSuspendedUntil()).isEqualTo(until);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(type, ':', status, ':', report_id, ':', member_id) FROM sanction", String.class))
                .isEqualTo("TEMPORARY_72H:ACTIVE:" + reportId + ":" + target);
        assertThat(jdbc.queryForObject("SELECT ends_at FROM sanction", LocalDateTime.class))
                .isEqualTo(LocalDateTime.ofInstant(until, ZoneOffset.UTC));
        assertThat(jdbc.queryForObject("SELECT urgent FROM member_report WHERE id = ?", Boolean.class, reportId))
                .isTrue();
        assertThat(sessionCount(target)).isZero();
        assertThat(mvc.get().uri("/api/me").cookie(targetFirst).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/me").cookie(targetSecond).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/me").cookie(reporterSession).exchange()).hasStatus(HttpStatus.OK);
        assertThat(sessionCount(reporter)).isEqualTo(1);
        MvcTestResult login = loginRequest(target);
        assertThat(login).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(login).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_SUSPENDED");
    }

    @Test
    @DisplayName("[SN-05] 같은 대상에게 긴급 신고가 두 건 오면 임시 정지 기록이 하나씩 생기고 정지 종료 시각은 늦은 쪽이다")
    void secondUrgentReportExtendsSuspension() {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        long target = joinedMember(basecampId);
        Cookie firstReporter = verifiedSession(joinedMember(basecampId));
        Cookie secondReporter = verifiedSession(joinedMember(basecampId));

        post(firstReporter, memberReport(target, basecampId, "HARASSMENT_OR_THREAT"));
        clock.advance(Duration.ofHours(1));
        MvcTestResult second = post(secondReporter, memberReport(target, basecampId, "HARASSMENT_OR_THREAT"));

        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(count("sanction")).isEqualTo(2);
        assertThat(memberRepository.findById(target).orElseThrow().getSuspendedUntil())
                .isEqualTo(clock.instant().plus(Duration.ofHours(72)));
    }

    private long joinedMember(long basecampId) {
        long memberId = fixture.saveVerifiedMember(TestSequence.nickname());
        fixture.insertMember(basecampId, memberId, "MEMBER", "ACTIVE");
        return memberId;
    }

    private long applicant(long basecampId, String status) {
        long memberId = fixture.saveVerifiedMember(TestSequence.nickname());
        fixture.insertApplication(basecampId, memberId, status);
        return memberId;
    }

    // 완료된 지 elapsed가 지난 베이스캠프와 그 멤버 한 명이다. 작성 기한(14일)을 넘기면 받은 후기가 공개된다.
    private CompletedScenario completedScenario(Duration elapsed) {
        long basecampId = fixture.saveBasecamp("COMPLETED", clock.instant().minus(elapsed));
        return new CompletedScenario(basecampId, fixture.leaderIdOf(basecampId), joinedMember(basecampId));
    }

    private record CompletedScenario(long basecampId, long leaderId, long reporterId) {}

    // 로그인할 수 있도록 비밀번호 해시를 채우고, 실제 흐름대로 로그인한 세션에서 인증 코드를 확인해 이메일 인증 권한을 얻는다.
    private Cookie verifiedSession(long memberId) {
        Cookie session = login(memberId);
        String code = emailVerificationService
                .issueFor(memberId, LOCAL_IP)
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

    private Cookie login(long memberId) {
        MvcTestResult result = loginRequest(memberId);
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }

    private MvcTestResult loginRequest(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
        return mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult loginRequest(Member member) {
        return loginRequest(member.getId());
    }

    private MvcTestResult post(Cookie session, String body) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.post().uri(PATH).with(csrf());
        if (session != null) {
            request.cookie(session);
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static String memberReport(long targetId, long basecampId, String type) {
        return body(targetId, basecampId, "MEMBER", null, type);
    }

    private static String reviewReport(long targetId, long basecampId, long reviewId) {
        return body(targetId, basecampId, "REVIEW", reviewId, "INAPPROPRIATE_REVIEW");
    }

    private static String body(long targetId, long basecampId, String kind, Long reviewId, String type) {
        String reviewField = reviewId == null ? "" : ",\"companionReviewId\":" + reviewId;
        return "{\"targetMemberId\":%d,\"basecampId\":%d,\"kind\":\"%s\"%s,\"type\":\"%s\",\"content\":\"신고 내용\"}"
                .formatted(targetId, basecampId, kind, reviewField, type);
    }

    private static Object bodyJson(MvcTestResult result, String path) {
        return com.jayway.jsonpath.JsonPath.read(
                new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8),
                path);
    }

    private int sessionCount(long memberId) {
        return count("SPRING_SESSION WHERE PRINCIPAL_NAME = ?", String.valueOf(memberId));
    }

    private String memberStatus(long memberId) {
        return jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, memberId);
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }
}
