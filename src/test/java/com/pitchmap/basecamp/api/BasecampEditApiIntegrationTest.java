package com.pitchmap.basecamp.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class BasecampEditApiIntegrationTest {

    private static final String CONTACT = "https://open.kakao.com/o/example";

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

    private BasecampApiFixture fixture;
    private Member leader;
    private Cookie leaderSession;
    private Member member;
    private Cookie memberSession;
    private long basecampId;

    @BeforeEach
    void setUp() {
        fixture = new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        leader = fixture.saveMember();
        leaderSession = fixture.identityVerifiedSession(leader, "FEMALE");
        member = fixture.saveMember();
        memberSession = fixture.identityVerifiedSession(member, "MALE");
        long spotId = fixture.insertSpot("개머리언덕", 37.25, 127.25);
        basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", BasecampApiFixture.DEFAULT_START_DATE);
        fixture.insertMemberRow(basecampId, member, "MEMBER", "ACTIVE");
    }

    @Test
    @DisplayName("[F-14][BC-23] 캠프 리더가 연락 수단을 등록하면 200이고 저장된다")
    void leaderRegistersContact() {
        // when
        MvcTestResult result = send(HttpMethod.PUT, "/contact", leaderSession, contactBody(CONTACT));

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("RECRUITING");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("contact_info", CONTACT);
    }

    @Test
    @DisplayName("[F-14][BC-23] 연락 수단은 확정된 뒤에야 멤버의 상세 응답에 나타나고, 확정 전에는 필드가 없다")
    void contactAppearsToMemberOnlyAfterConfirm() {
        // given
        assertThat(send(HttpMethod.PUT, "/contact", leaderSession, contactBody(CONTACT)))
                .hasStatus(HttpStatus.OK);

        // when
        MvcTestResult beforeConfirm = detail(memberSession);
        assertThat(mvc.post()
                        .uri("/api/basecamps/" + basecampId + "/confirm")
                        .with(csrf())
                        .cookie(leaderSession)
                        .exchange())
                .hasStatus(HttpStatus.OK);
        MvcTestResult afterConfirm = detail(memberSession);

        // then
        assertThat(beforeConfirm).hasStatus(HttpStatus.OK);
        assertThat(beforeConfirm).bodyJson().doesNotHavePath("$.contactInfo");
        assertThat(afterConfirm).bodyJson().extractingPath("$.contactInfo").isEqualTo(CONTACT);
    }

    @Test
    @DisplayName("[F-14][BC-23] 취소된 베이스캠프에 연락 수단을 등록하면 409 BASECAMP_INVALID_STATE다")
    void contactRejectsCanceledBasecamp() {
        // given
        fixture.setStatus(basecampId, "CANCELED");

        // when
        MvcTestResult result = send(HttpMethod.PUT, "/contact", leaderSession, contactBody(CONTACT));

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("contact_info", null);
    }

    @Test
    @DisplayName("[F-14][BC-23] 캠프 리더가 아닌 멤버가 연락 수단을 등록하면 403 ACCESS_DENIED, 없는 베이스캠프는 404 NOT_FOUND다")
    void contactRequiresLeader() {
        // when
        MvcTestResult byMember = send(HttpMethod.PUT, "/contact", memberSession, contactBody(CONTACT));
        MvcTestResult unknown =
                send(HttpMethod.PUT, basecampId + 1000, "/contact", leaderSession, contactBody(CONTACT));

        // then
        assertFailure(byMember, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertFailure(unknown, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("contact_info", null);
    }

    @Test
    @DisplayName("[F-14][BC-02] 정원만 보내면 정원이 늘고 제목, 설명, 합류 조건은 그대로다")
    void capacityIncreaseKeepsUnsentFields() {
        // given
        Map<String, Object> before = fixture.basecampRow(basecampId);

        // when
        MvcTestResult result = send(HttpMethod.PATCH, "", leaderSession, "{\"capacity\":5}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("RECRUITING");
        Map<String, Object> after = fixture.basecampRow(basecampId);
        assertThat(after.get("capacity")).isEqualTo(5);
        assertThat(after.get("title")).isEqualTo(before.get("title"));
        assertThat(after.get("description")).isEqualTo(before.get("description"));
        assertThat(after.get("same_gender_only")).isEqualTo(before.get("same_gender_only"));
    }

    @Test
    @DisplayName("[F-14] 제목과 설명을 고치면 보낸 값으로 바뀌고 정원은 그대로다")
    void titleAndDescriptionAreReplaced() {
        // when
        MvcTestResult result =
                send(HttpMethod.PATCH, "", leaderSession, "{\"title\":\"새 제목\",\"description\":\"새 설명\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(fixture.basecampRow(basecampId))
                .containsEntry("title", "새 제목")
                .containsEntry("description", "새 설명")
                .containsEntry("capacity", 4);
    }

    @Test
    @DisplayName("[F-14][BC-11] 정원이 차서 자동 마감된 베이스캠프의 정원을 늘리면 다시 RECRUITING이 된다")
    void capacityIncreaseReopensAutoClosedBasecamp() {
        // given
        fixture.setCapacity(basecampId, 2);
        fixture.setClosedReason(basecampId, "CLOSED", "AUTO_FULL");

        // when
        MvcTestResult result = send(HttpMethod.PATCH, "", leaderSession, "{\"capacity\":3}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("RECRUITING");
        assertThat(fixture.basecampRow(basecampId))
                .containsEntry("status", "RECRUITING")
                .containsEntry("closed_reason", null);
    }

    @Test
    @DisplayName("[F-14][BC-02] 정원을 줄이면 400 BASECAMP_CAPACITY_INVALID이고, 2~6명 밖의 값도 같은 오류다")
    void capacityDecreaseAndOutOfRangeAreRejected() {
        // when
        MvcTestResult decrease = send(HttpMethod.PATCH, "", leaderSession, "{\"capacity\":3}");
        MvcTestResult tooMany = send(HttpMethod.PATCH, "", leaderSession, "{\"capacity\":7}");

        // then
        assertFailure(decrease, HttpStatus.BAD_REQUEST, "BASECAMP_CAPACITY_INVALID");
        assertFailure(tooMany, HttpStatus.BAD_REQUEST, "BASECAMP_CAPACITY_INVALID");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("capacity", 4);
    }

    @Test
    @DisplayName("[F-14] 확정된 베이스캠프를 고치면 409 BASECAMP_INVALID_STATE다")
    void reviseRejectsConfirmedBasecamp() {
        // given
        fixture.setStatus(basecampId, "CONFIRMED");

        // when
        MvcTestResult result = send(HttpMethod.PATCH, "", leaderSession, "{\"capacity\":5}");

        // then
        assertFailure(result, HttpStatus.CONFLICT, "BASECAMP_INVALID_STATE");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("capacity", 4);
    }

    @Test
    @DisplayName("[F-14][BC-05] sameGenderOnly를 true로 보내면 캠프 리더의 본인확인 성별이 저장되고, 보낸 합류 조건이 이전 조건을 통째로 바꾼다")
    void joinConditionReplacesWholeConditionWithLeaderGender() {
        // given
        assertThat(
                        send(
                                HttpMethod.PATCH,
                                "",
                                leaderSession,
                                "{\"joinCondition\":{\"minTrustLevel\":2,\"ageGroupMin\":20,\"ageGroupMax\":30,\"sameGenderOnly\":false}}"))
                .hasStatus(HttpStatus.OK);

        // when
        MvcTestResult result =
                send(HttpMethod.PATCH, "", leaderSession, "{\"joinCondition\":{\"sameGenderOnly\":true}}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(fixture.basecampRow(basecampId))
                .containsEntry("same_gender_only", true)
                .containsEntry("required_gender", "FEMALE")
                .containsEntry("min_trust_level", null)
                .containsEntry("age_group_min", null)
                .containsEntry("age_group_max", null);
    }

    @Test
    @DisplayName("[F-14][BC-05] 합류 조건에서 sameGenderOnly를 빼고 보내면 동성 조건 없이 보낸 값만 저장된다")
    void joinConditionWithoutSameGenderOnly() {
        // when
        MvcTestResult result = send(HttpMethod.PATCH, "", leaderSession, "{\"joinCondition\":{\"minTrustLevel\":2}}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(fixture.basecampRow(basecampId))
                .containsEntry("min_trust_level", 2)
                .containsEntry("same_gender_only", false)
                .containsEntry("required_gender", null);
    }

    @Test
    @DisplayName("[F-14][BC-05] 연령대의 한쪽만 보내면 400 INVALID_INPUT이고 합류 조건은 그대로다")
    void halfAgeGroupIsRejected() {
        // when
        MvcTestResult result = send(HttpMethod.PATCH, "", leaderSession, "{\"joinCondition\":{\"ageGroupMin\":20}}");

        // then
        assertFailure(result, HttpStatus.BAD_REQUEST, "INVALID_INPUT");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("age_group_min", null);
    }

    @Test
    @DisplayName("[F-14] 캠프 리더가 아닌 멤버가 고치면 403 ACCESS_DENIED, 없는 베이스캠프는 404 NOT_FOUND다")
    void reviseRequiresLeader() {
        // when
        MvcTestResult byMember = send(HttpMethod.PATCH, "", memberSession, "{\"capacity\":5}");
        MvcTestResult unknown = send(HttpMethod.PATCH, basecampId + 1000, "", leaderSession, "{\"capacity\":5}");

        // then
        assertFailure(byMember, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertFailure(unknown, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(fixture.basecampRow(basecampId)).containsEntry("capacity", 4);
    }

    private MvcTestResult detail(Cookie session) {
        return mvc.get().uri("/api/basecamps/" + basecampId).cookie(session).exchange();
    }

    private static String contactBody(String contactInfo) {
        return "{\"contactInfo\":\"%s\"}".formatted(contactInfo);
    }

    private MvcTestResult send(HttpMethod method, String suffix, Cookie session, String body) {
        return send(method, basecampId, suffix, session, body);
    }

    private MvcTestResult send(HttpMethod method, long id, String suffix, Cookie session, String body) {
        return mvc.method(method)
                .uri("/api/basecamps/" + id + suffix)
                .with(csrf())
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static void assertFailure(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
