package com.pitchmap.basecamp.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class BasecampDetailApiIntegrationTest {

    private static final String CONTACT = "https://open.kakao.com/o/abc";

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

    private BasecampApiFixture fixture;
    private Member leader;
    private Member member;
    private Member applicant;
    private Member outsider;
    private Cookie leaderSession;
    private Cookie memberSession;
    private Cookie applicantSession;
    private Cookie outsiderSession;
    private long spotId;

    @BeforeEach
    void setUp() {
        fixture = new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        leader = fixture.saveMember();
        member = fixture.saveMember();
        applicant = fixture.saveMember();
        outsider = fixture.saveMember();
        leaderSession = fixture.identityVerifiedSession(leader, "FEMALE");
        memberSession = fixture.identityVerifiedSession(member, "MALE");
        applicantSession = fixture.login(applicant);
        outsiderSession = fixture.login(outsider);
        spotId = fixture.insertSpot("개머리언덕", 37.25, 127.25);
    }

    @Test
    @DisplayName("[F-12][NFR-11] 비로그인 요청자는 기본 정보와 멤버의 닉네임·역할만 보고, 연락 수단과 프로필 요약 필드는 없다")
    void anonymousSeesOnlyNicknames() {
        // given
        long id = confirmedBasecampWithContact();

        // when
        MvcTestResult result = mvc.get().uri("/api/basecamps/" + id).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.basecampId").isEqualTo((int) id);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("굴업도 주말 1박");
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("함께 가요");
        assertThat(result).bodyJson().extractingPath("$.spot.name").isEqualTo("개머리언덕");
        assertThat(result).bodyJson().extractingPath("$.spot.type").isEqualTo("BAKJI");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(result).bodyJson().extractingPath("$.capacity").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.headcount").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.myRelation").isEqualTo("NONE");
        assertThat(result).bodyJson().extractingPath("$.leader.nickname").isEqualTo(leader.getNickname());
        assertThat(result).bodyJson().extractingPath("$.members[0].role").isEqualTo("LEADER");
        assertThat(result).bodyJson().extractingPath("$.members[1].nickname").isEqualTo(member.getNickname());
        assertThat(result).bodyJson().extractingPath("$.safetyNotice").isNotNull();
        assertThat(result).bodyJson().doesNotHavePath("$.contactInfo");
        assertThat(result).bodyJson().doesNotHavePath("$.leader.trustLevel");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].ageGroup");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].ageGroupVerified");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].gender");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].genderVerified");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].trustLevel");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].completedCompanions");
        assertNoEmailOrBirthYear(result);
    }

    @Test
    @DisplayName("[F-12][NFR-11] 로그인한 회원은 멤버의 본인확인 연령대·성별과 신뢰 단계를 보지만 연락 수단은 못 본다")
    void loggedInOutsiderSeesProfilesButNoContact() {
        // given
        long id = confirmedBasecampWithContact();

        // when
        MvcTestResult result =
                mvc.get().uri("/api/basecamps/" + id).cookie(outsiderSession).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.myRelation").isEqualTo("NONE");
        assertThat(result).bodyJson().extractingPath("$.leader.memberId").isEqualTo((int) (long) leader.getId());
        assertThat(result).bodyJson().extractingPath("$.leader.trustLevel").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.members[0].role").isEqualTo("LEADER");
        assertThat(result).bodyJson().extractingPath("$.members[0].ageGroup").isEqualTo("TWENTIES");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.members[0].ageGroupVerified")
                .isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.members[0].gender").isEqualTo("FEMALE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.members[0].genderVerified")
                .isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.members[0].trustLevel").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.members[0].completedCompanions")
                .isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.members[1].gender").isEqualTo("MALE");
        assertThat(result).bodyJson().doesNotHavePath("$.contactInfo");
        assertThat(result).bodyJson().extractingPath("$.safetyNotice").isNotNull();
        assertNoEmailOrBirthYear(result);
    }

    @Test
    @DisplayName("[F-12][NFR-11] 대기 중인 신청자는 myRelation이 APPLICANT이고 연락 수단은 못 본다")
    void applicantSeesApplicantRelationWithoutContact() {
        // given
        long id = confirmedBasecampWithContact();
        fixture.insertApplicationRow(id, applicant, "PENDING");

        // when
        MvcTestResult result =
                mvc.get().uri("/api/basecamps/" + id).cookie(applicantSession).exchange();

        // then
        assertThat(result).bodyJson().extractingPath("$.myRelation").isEqualTo("APPLICANT");
        assertThat(result).bodyJson().doesNotHavePath("$.contactInfo");
    }

    @Test
    @DisplayName("[F-12][BC-23] 확정된 베이스캠프의 멤버와 캠프 리더만 연락 수단을 보고, myRelation은 각각 MEMBER와 LEADER다")
    void confirmedMemberAndLeaderSeeContact() {
        // given
        long id = confirmedBasecampWithContact();

        // when
        MvcTestResult asMember =
                mvc.get().uri("/api/basecamps/" + id).cookie(memberSession).exchange();
        MvcTestResult asLeader =
                mvc.get().uri("/api/basecamps/" + id).cookie(leaderSession).exchange();

        // then
        assertThat(asMember).bodyJson().extractingPath("$.myRelation").isEqualTo("MEMBER");
        assertThat(asMember).bodyJson().extractingPath("$.contactInfo").isEqualTo(CONTACT);
        assertThat(asLeader).bodyJson().extractingPath("$.myRelation").isEqualTo("LEADER");
        assertThat(asLeader).bodyJson().extractingPath("$.contactInfo").isEqualTo(CONTACT);
    }

    @Test
    @DisplayName("[F-12][BC-23] 확정 전에는 멤버와 캠프 리더도 연락 수단을 못 보고, 확정돼도 등록된 값이 없으면 필드가 없다")
    void noContactBeforeConfirmationOrWithoutValue() {
        // given
        long recruiting = basecamp("RECRUITING");
        jdbc.update("UPDATE basecamp SET contact_info = ? WHERE id = ?", CONTACT, recruiting);
        long confirmedWithoutContact = basecamp("CONFIRMED");

        // when
        MvcTestResult beforeConfirm = mvc.get()
                .uri("/api/basecamps/" + recruiting)
                .cookie(memberSession)
                .exchange();
        MvcTestResult noValue = mvc.get()
                .uri("/api/basecamps/" + confirmedWithoutContact)
                .cookie(memberSession)
                .exchange();

        // then
        assertThat(beforeConfirm).hasStatus(HttpStatus.OK);
        assertThat(beforeConfirm).bodyJson().doesNotHavePath("$.contactInfo");
        assertThat(noValue).hasStatus(HttpStatus.OK);
        assertThat(noValue).bodyJson().doesNotHavePath("$.contactInfo");
    }

    @Test
    @DisplayName("[F-12][BC-23] 완료 후 7일 정각까지는 멤버에게 연락 수단이 보이고, 1초가 지나면 보이지 않는다")
    void contactVisibleUntilSevenDaysAfterCompletion() {
        // given
        long id = basecamp("COMPLETED");
        fixture.insertMemberRow(id, member, "MEMBER", "ACTIVE");
        jdbc.update(
                "UPDATE basecamp SET contact_info = ?, completed_at = '2026-10-05 03:00:00' WHERE id = ?", CONTACT, id);
        clock.setInstant(MutableClock.DEFAULT_INSTANT.plus(Duration.ofDays(7)));

        // when
        MvcTestResult onBoundary =
                mvc.get().uri("/api/basecamps/" + id).cookie(memberSession).exchange();
        clock.setInstant(MutableClock.DEFAULT_INSTANT.plus(Duration.ofDays(7)).plusSeconds(1));
        MvcTestResult afterBoundary =
                mvc.get().uri("/api/basecamps/" + id).cookie(memberSession).exchange();

        // then
        assertThat(onBoundary).bodyJson().extractingPath("$.contactInfo").isEqualTo(CONTACT);
        assertThat(afterBoundary).hasStatus(HttpStatus.OK);
        assertThat(afterBoundary).bodyJson().doesNotHavePath("$.contactInfo");
    }

    @Test
    @DisplayName("[F-12] 탈퇴한 멤버는 멤버 목록과 headcount에 없고, 캠프 리더가 맨 앞이다")
    void leftMembersAreNotListed() {
        // given
        long id = basecamp("RECRUITING");
        Member leaver = fixture.saveMember();
        fixture.insertMemberRow(id, leaver, "MEMBER", "LEFT");
        fixture.insertMemberRow(id, member, "MEMBER", "ACTIVE");

        // when
        MvcTestResult result =
                mvc.get().uri("/api/basecamps/" + id).cookie(outsiderSession).exchange();

        // then
        assertThat(result).bodyJson().extractingPath("$.headcount").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.members").asList().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$.members[0].role").isEqualTo("LEADER");
        assertThat(result).bodyJson().extractingPath("$.members[1].memberId").isEqualTo((int) (long) member.getId());
    }

    @Test
    @DisplayName("[F-12] 마감·취소된 베이스캠프도 상세를 볼 수 있고, 없는 베이스캠프는 404 NOT_FOUND다")
    void detailIsVisibleInEveryStatusAndMissingIsNotFound() {
        // given
        long closed = basecamp("CLOSED");
        long canceled = basecamp("CANCELED");

        // when
        MvcTestResult closedResult = mvc.get().uri("/api/basecamps/" + closed).exchange();
        MvcTestResult canceledResult =
                mvc.get().uri("/api/basecamps/" + canceled).exchange();
        MvcTestResult missing =
                mvc.get().uri("/api/basecamps/" + (canceled + 1000)).exchange();

        // then
        assertThat(closedResult).bodyJson().extractingPath("$.status").isEqualTo("CLOSED");
        assertThat(canceledResult).bodyJson().extractingPath("$.status").isEqualTo("CANCELED");
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-12] 캠프 리더의 멤버 행이 탈퇴 상태인 취소된 베이스캠프도 200이고, 리더는 leader에만 있고 members에는 없다")
    void leaderWhoLeftIsStillShownAsLeader() {
        // given
        long id = basecamp("CANCELED");
        jdbc.update("UPDATE basecamp_member SET status = 'LEFT' WHERE basecamp_id = ?", id);
        fixture.insertMemberRow(id, member, "MEMBER", "ACTIVE");

        // when
        MvcTestResult anonymous = mvc.get().uri("/api/basecamps/" + id).exchange();
        MvcTestResult loggedIn =
                mvc.get().uri("/api/basecamps/" + id).cookie(outsiderSession).exchange();

        // then
        for (MvcTestResult result : new MvcTestResult[] {anonymous, loggedIn}) {
            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(result).bodyJson().extractingPath("$.leader.memberId").isEqualTo((int) (long) leader.getId());
            assertThat(result).bodyJson().extractingPath("$.members").asList().hasSize(1);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.members[0].memberId")
                    .isEqualTo((int) (long) member.getId());
        }
        assertThat(loggedIn).bodyJson().extractingPath("$.leader.trustLevel").isEqualTo(1);
    }

    private long basecamp(String status) {
        return fixture.insertBasecamp(leader, spotId, status, BasecampApiFixture.DEFAULT_START_DATE);
    }

    // 캠프 리더와 멤버 한 명이 있는 확정된 베이스캠프에 연락 수단을 등록해 둔다.
    private long confirmedBasecampWithContact() {
        long id = basecamp("CONFIRMED");
        fixture.insertMemberRow(id, member, "MEMBER", "ACTIVE");
        jdbc.update("UPDATE basecamp SET contact_info = ? WHERE id = ?", CONTACT, id);
        return id;
    }

    // 응답 본문에 이메일과 출생연도가 어떤 이름으로도 없는지 문자열로 확인한다.
    private void assertNoEmailOrBirthYear(MvcTestResult result) {
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body)
                .doesNotContain("email")
                .doesNotContain("birthYear")
                .doesNotContain(leader.getEmail())
                .doesNotContain(member.getEmail());
    }
}
