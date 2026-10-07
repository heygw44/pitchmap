package com.pitchmap.basecamp.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
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
class BasecampSearchApiIntegrationTest {

    private static final String SEARCH = "/api/basecamps?lat=37.25&lng=127.25&radiusKm=10";
    private static final LocalDate START = BasecampApiFixture.DEFAULT_START_DATE;

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
    private long spotId;

    @BeforeEach
    void setUp() {
        fixture = new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        leader = fixture.saveMember();
        spotId = fixture.insertSpot("능선 끝 평지", 37.25, 127.25);
    }

    @Test
    @DisplayName("[F-12] 비로그인 검색은 모집 중 베이스캠프를 장소·합류 조건과 함께 주고 canApply와 unmetReasons 필드는 뺀다")
    void anonymousSearchOmitsEligibility() {
        // given
        long basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", START);
        fixture.insertBasecamp(leader, spotId, "CLOSED", START);

        // when
        MvcTestResult result = mvc.get().uri(SEARCH).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.content[0].basecampId").isEqualTo((int) basecampId);
        assertThat(result).bodyJson().extractingPath("$.content[0].title").isEqualTo("굴업도 주말 1박");
        assertThat(result).bodyJson().extractingPath("$.content[0].spot.name").isEqualTo("능선 끝 평지");
        assertThat(result).bodyJson().extractingPath("$.content[0].spot.type").isEqualTo("BAKJI");
        assertThat(result).bodyJson().extractingPath("$.content[0].spot.lat").isEqualTo(37.25);
        assertThat(result).bodyJson().extractingPath("$.content[0].spot.lng").isEqualTo(127.25);
        assertThat(result).bodyJson().extractingPath("$.content[0].startDate").isEqualTo("2026-10-20");
        assertThat(result).bodyJson().extractingPath("$.content[0].capacity").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.content[0].headcount").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("RECRUITING");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].joinCondition.sameGenderOnly")
                .isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].canApply");
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].unmetReasons");
    }

    @Test
    @DisplayName("[F-12][BC-05] 본인확인 전 회원은 신뢰 단계 0이라 TRUST_LEVEL로 신청할 수 없다고 나온다")
    void notIdentityVerifiedMemberHasTrustLevelReason() {
        // given
        fixture.insertBasecamp(leader, spotId, "RECRUITING", START);
        Cookie session = fixture.login(fixture.saveMember());

        // when
        MvcTestResult result = mvc.get().uri(SEARCH).cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].canApply").isEqualTo(false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of("TRUST_LEVEL"));
    }

    @Test
    @DisplayName("[F-12][BC-05] 본인확인한 회원은 조건이 없는 베이스캠프에 신청할 수 있고, 최소 단계 2 조건에는 TRUST_LEVEL이 나온다")
    void identityVerifiedMemberCanApplyUnlessMinLevelIsHigher() {
        // given
        fixture.insertBasecamp(leader, spotId, "RECRUITING", START);
        long strict = fixture.insertBasecamp(leader, spotId, "RECRUITING", START.plusDays(1));
        jdbc.update("UPDATE basecamp SET min_trust_level = 2 WHERE id = ?", strict);
        Cookie session = fixture.identityVerifiedSession(fixture.saveMember(), "MALE");

        // when
        MvcTestResult result = mvc.get().uri(SEARCH).cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].canApply").isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of());
        assertThat(result).bodyJson().extractingPath("$.content[1].canApply").isEqualTo(false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[1].unmetReasons")
                .isEqualTo(List.of("TRUST_LEVEL"));
    }

    @Test
    @DisplayName("[F-12][BC-07] 캠프 리더 본인과 대기 신청자는 ALREADY_JOINED, 강퇴된 회원은 REAPPLY_NOT_ALLOWED가 나온다")
    void priorRelationReasons() {
        // given
        Member applicant = fixture.saveMember();
        Member kicked = fixture.saveMember();
        long basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", START);
        fixture.insertApplicationRow(basecampId, applicant, "PENDING");
        fixture.insertMemberRow(basecampId, kicked, "MEMBER", "KICKED");
        Cookie leaderSession = fixture.identityVerifiedSession(leader, "FEMALE");
        Cookie applicantSession = fixture.identityVerifiedSession(applicant, "FEMALE");
        Cookie kickedSession = fixture.identityVerifiedSession(kicked, "FEMALE");

        // when
        MvcTestResult asLeader = mvc.get().uri(SEARCH).cookie(leaderSession).exchange();
        MvcTestResult asApplicant =
                mvc.get().uri(SEARCH).cookie(applicantSession).exchange();
        MvcTestResult asKicked = mvc.get().uri(SEARCH).cookie(kickedSession).exchange();

        // then
        assertThat(asLeader)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of("ALREADY_JOINED"));
        assertThat(asApplicant)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of("ALREADY_JOINED"));
        assertThat(asKicked)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of("REAPPLY_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("[F-12][BC-05] 동성만 받는 조건이면 다른 성별은 GENDER, 같은 성별은 신청 가능이다")
    void sameGenderCondition() {
        // given
        long basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", START);
        jdbc.update("UPDATE basecamp SET same_gender_only = TRUE, required_gender = 'FEMALE' WHERE id = ?", basecampId);
        Cookie male = fixture.identityVerifiedSession(fixture.saveMember(), "MALE");
        Cookie female = fixture.identityVerifiedSession(fixture.saveMember(), "FEMALE");

        // when
        MvcTestResult asMale = mvc.get().uri(SEARCH).cookie(male).exchange();
        MvcTestResult asFemale = mvc.get().uri(SEARCH).cookie(female).exchange();

        // then
        assertThat(asMale)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of("GENDER"));
        assertThat(asMale)
                .bodyJson()
                .extractingPath("$.content[0].joinCondition.sameGenderOnly")
                .isEqualTo(true);
        assertThat(asFemale).bodyJson().extractingPath("$.content[0].canApply").isEqualTo(true);
    }

    @Test
    @DisplayName("[F-12][BC-05] 연령대 조건 밖인 회원은 AGE_GROUP이 나온다")
    void ageGroupCondition() {
        // given
        long basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", START);
        jdbc.update("UPDATE basecamp SET age_group_min = 40, age_group_max = 50 WHERE id = ?", basecampId);
        Cookie session = fixture.identityVerifiedSession(fixture.saveMember(), "MALE");

        // when
        MvcTestResult result = mvc.get().uri(SEARCH).cookie(session).exchange();

        // then
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of("AGE_GROUP"));
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].joinCondition.ageGroupMin")
                .isEqualTo(40);
    }

    @Test
    @DisplayName("[F-12][BC-06] 기간이 하루라도 겹치는 확정된 베이스캠프의 멤버는 DATE_CONFLICT가 나오고, 겹치지 않으면 신청 가능이다")
    void dateConflictWithConfirmedBasecamp() {
        // given
        Member viewer = fixture.saveMember();
        Cookie session = fixture.identityVerifiedSession(viewer, "MALE");
        long target = fixture.insertBasecamp(leader, spotId, "RECRUITING", START);
        long otherSpot = fixture.insertSpot("다른 장소", 37.5, 127.5);
        // 확정된 베이스캠프는 10월 22일에 끝나고, 검색 대상은 10월 20일~22일이라 겹친다.
        long confirmed = fixture.insertBasecamp(fixture.saveMember(), otherSpot, "CONFIRMED", START.minusDays(2));
        fixture.insertMemberRow(confirmed, viewer, "MEMBER", "ACTIVE");
        long farTarget = fixture.insertBasecamp(leader, spotId, "RECRUITING", START.plusDays(10));

        // when
        MvcTestResult result = mvc.get().uri(SEARCH).cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].basecampId").isEqualTo((int) target);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of("DATE_CONFLICT"));
        assertThat(result).bodyJson().extractingPath("$.content[1].basecampId").isEqualTo((int) farTarget);
        assertThat(result).bodyJson().extractingPath("$.content[1].canApply").isEqualTo(true);
    }

    @Test
    @DisplayName("[F-12] 지도 영역·출발일·남은 자리 조건과 페이지를 보내면 조건에 맞는 베이스캠프만 한 페이지씩 준다")
    void areaDateVacancyAndPaging() {
        // given
        long first = fixture.insertBasecamp(leader, spotId, "RECRUITING", START);
        long second = fixture.insertBasecamp(leader, spotId, "RECRUITING", START.plusDays(1));
        fixture.insertBasecamp(leader, spotId, "RECRUITING", START.plusDays(30));
        long full = fixture.insertBasecamp(leader, spotId, "RECRUITING", START.plusDays(2));
        for (int i = 0; i < 3; i++) {
            fixture.insertMemberRow(full, fixture.saveMember(), "MEMBER", "ACTIVE");
        }
        String query = "/api/basecamps?swLat=37.0&swLng=127.0&neLat=38.0&neLng=128.0"
                + "&fromDate=2026-10-20&toDate=2026-10-25&hasVacancy=true&size=1";

        // when
        MvcTestResult firstPage = mvc.get().uri(query).exchange();
        MvcTestResult secondPage = mvc.get().uri(query + "&page=1").exchange();

        // then
        assertThat(firstPage)
                .bodyJson()
                .extractingPath("$.content[0].basecampId")
                .isEqualTo((int) first);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(secondPage)
                .bodyJson()
                .extractingPath("$.content[0].basecampId")
                .isEqualTo((int) second);
        assertThat(secondPage).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(secondPage).bodyJson().extractingPath("$.page").isEqualTo(1);
    }

    @Test
    @DisplayName("[F-12] 지역이 없거나 반경이 50km를 넘으면 400 INVALID_INPUT이다")
    void invalidRegionIsRejected() {
        // when
        MvcTestResult noRegion = mvc.get().uri("/api/basecamps").exchange();
        MvcTestResult tooLarge = mvc.get()
                .uri("/api/basecamps?lat=37.25&lng=127.25&radiusKm=50.1")
                .exchange();

        // then
        assertThat(noRegion).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(noRegion).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(tooLarge).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooLarge).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }
}
