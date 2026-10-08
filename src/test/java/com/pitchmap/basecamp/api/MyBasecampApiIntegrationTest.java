package com.pitchmap.basecamp.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
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
class MyBasecampApiIntegrationTest {

    private static final String MINE = "/api/me/basecamps";
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
    private Member me;
    private Member other;
    private Cookie session;
    private long spotId;

    @BeforeEach
    void setUp() {
        fixture = new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        me = fixture.saveMember();
        other = fixture.saveMember();
        session = fixture.login(me);
        spotId = fixture.insertSpot("능선 끝 평지", 37.25, 127.25);
    }

    @Test
    @DisplayName("[F-13] 이메일 인증 전의 회원도 내 베이스캠프를 조회하고, 리더·멤버·신청자 관계를 myRelation으로 구분해 받는다")
    void returnsAllThreeRelations() {
        // given
        long led = fixture.insertBasecamp(me, spotId, "RECRUITING", START.plusDays(2));
        long joined = fixture.insertBasecamp(other, spotId, "CONFIRMED", START.plusDays(1));
        fixture.insertMemberRow(joined, me, "MEMBER", "ACTIVE");
        long applied = fixture.insertBasecamp(other, spotId, "RECRUITING", START);
        fixture.insertApplicationRow(applied, me, "PENDING");

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(3);
        assertThat(result).bodyJson().extractingPath("$.content[0].basecampId").isEqualTo((int) led);
        assertThat(result).bodyJson().extractingPath("$.content[0].myRelation").isEqualTo("LEADER");
        assertThat(result).bodyJson().extractingPath("$.content[1].basecampId").isEqualTo((int) joined);
        assertThat(result).bodyJson().extractingPath("$.content[1].myRelation").isEqualTo("MEMBER");
        assertThat(result).bodyJson().extractingPath("$.content[2].basecampId").isEqualTo((int) applied);
        assertThat(result).bodyJson().extractingPath("$.content[2].myRelation").isEqualTo("APPLICANT");
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(20);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-13] 항목에는 제목·장소 요약·일정·정원·상태가 담기고 headcount는 캠프 리더를 포함한 ACTIVE 멤버 수다")
    void itemShapeAndHeadcount() {
        // given
        long basecampId = fixture.insertBasecamp(me, spotId, "RECRUITING", START);
        fixture.insertMemberRow(basecampId, other, "MEMBER", "ACTIVE");
        fixture.insertMemberRow(basecampId, fixture.saveMember(), "MEMBER", "LEFT");
        fixture.insertMemberRow(basecampId, fixture.saveMember(), "MEMBER", "KICKED");

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].title").isEqualTo("굴업도 주말 1박");
        assertThat(result).bodyJson().extractingPath("$.content[0].spot.spotId").isEqualTo((int) spotId);
        assertThat(result).bodyJson().extractingPath("$.content[0].spot.name").isEqualTo("능선 끝 평지");
        assertThat(result).bodyJson().extractingPath("$.content[0].spot.type").isEqualTo("BAKJI");
        assertThat(result).bodyJson().extractingPath("$.content[0].startDate").isEqualTo("2026-10-20");
        assertThat(result).bodyJson().extractingPath("$.content[0].endDate").isEqualTo("2026-10-22");
        assertThat(result).bodyJson().extractingPath("$.content[0].capacity").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.content[0].headcount").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("RECRUITING");
    }

    @Test
    @DisplayName("[F-13] relation을 보내면 그 관계의 베이스캠프만 준다")
    void filtersByRelation() {
        // given
        long led = fixture.insertBasecamp(me, spotId, "RECRUITING", START);
        long joined = fixture.insertBasecamp(other, spotId, "RECRUITING", START);
        fixture.insertMemberRow(joined, me, "MEMBER", "ACTIVE");
        long applied = fixture.insertBasecamp(other, spotId, "RECRUITING", START);
        fixture.insertApplicationRow(applied, me, "PENDING");

        // when
        MvcTestResult leader =
                mvc.get().uri(MINE + "?relation=LEADER").cookie(session).exchange();
        MvcTestResult member =
                mvc.get().uri(MINE + "?relation=MEMBER").cookie(session).exchange();
        MvcTestResult applicant =
                mvc.get().uri(MINE + "?relation=APPLICANT").cookie(session).exchange();

        // then
        assertThat(leader).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(leader).bodyJson().extractingPath("$.content[0].basecampId").isEqualTo((int) led);
        assertThat(member).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(member).bodyJson().extractingPath("$.content[0].basecampId").isEqualTo((int) joined);
        assertThat(applicant).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(applicant)
                .bodyJson()
                .extractingPath("$.content[0].basecampId")
                .isEqualTo((int) applied);
    }

    @Test
    @DisplayName("[F-13] status를 보내면 그 상태의 베이스캠프만 준다")
    void filtersByStatus() {
        // given
        fixture.insertBasecamp(me, spotId, "RECRUITING", START);
        long closed = fixture.insertBasecamp(me, spotId, "CLOSED", START);
        long confirmedApplied = fixture.insertBasecamp(other, spotId, "CLOSED", START);
        fixture.insertApplicationRow(confirmedApplied, me, "PENDING");

        // when
        MvcTestResult result =
                mvc.get().uri(MINE + "?status=CLOSED").cookie(session).exchange();
        MvcTestResult combined = mvc.get()
                .uri(MINE + "?status=CLOSED&relation=LEADER")
                .cookie(session)
                .exchange();
        MvcTestResult none =
                mvc.get().uri(MINE + "?status=COMPLETED").cookie(session).exchange();

        // then
        assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(combined).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(combined)
                .bodyJson()
                .extractingPath("$.content[0].basecampId")
                .isEqualTo((int) closed);
        assertThat(none).bodyJson().extractingPath("$.content").asList().isEmpty();
    }

    @Test
    @DisplayName("[F-13] 탈퇴·강퇴된 멤버 행과 거절·취소·만료된 신청 행은 목록에 없다")
    void excludesEndedRelations() {
        // given
        long left = fixture.insertBasecamp(other, spotId, "RECRUITING", START);
        fixture.insertMemberRow(left, me, "MEMBER", "LEFT");
        long kicked = fixture.insertBasecamp(other, spotId, "RECRUITING", START);
        fixture.insertMemberRow(kicked, me, "MEMBER", "KICKED");
        fixture.insertApplicationRow(fixture.insertBasecamp(other, spotId, "RECRUITING", START), me, "REJECTED");
        fixture.insertApplicationRow(fixture.insertBasecamp(other, spotId, "RECRUITING", START), me, "CANCELED");
        fixture.insertApplicationRow(fixture.insertBasecamp(other, spotId, "RECRUITING", START), me, "EXPIRED");

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content").asList().isEmpty();
    }

    @Test
    @DisplayName("[F-13] 다른 회원의 베이스캠프와 신청은 목록에 없다")
    void excludesOthersBasecamps() {
        // given
        long mine = fixture.insertBasecamp(me, spotId, "RECRUITING", START);
        fixture.insertBasecamp(other, spotId, "RECRUITING", START);
        long othersApplied = fixture.insertBasecamp(other, spotId, "RECRUITING", START);
        fixture.insertApplicationRow(othersApplied, fixture.saveMember(), "PENDING");

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then
        assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.content[0].basecampId").isEqualTo((int) mine);
    }

    @Test
    @DisplayName("[F-13] 장소가 숨겨져도 내 기록이라서 목록에 나온다")
    void includesBasecampOfHiddenSpot() {
        // given
        long hiddenSpot = fixture.insertSpot("숨겨진 평지", 37.3, 127.3);
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hiddenSpot);
        long basecampId = fixture.insertBasecamp(me, hiddenSpot, "RECRUITING", START);

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then
        assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.content[0].basecampId").isEqualTo((int) basecampId);
    }

    @Test
    @DisplayName("[F-13] 출발일이 늦은 순서로 정렬하고, 출발일이 같으면 베이스캠프 ID가 큰 순서다")
    void sortsByStartDateDescThenIdDesc() {
        // given
        long early = fixture.insertBasecamp(me, spotId, "RECRUITING", START);
        long sameDayFirst = fixture.insertBasecamp(me, spotId, "RECRUITING", START.plusDays(5));
        long sameDaySecond = fixture.insertBasecamp(me, spotId, "RECRUITING", START.plusDays(5));
        long late = fixture.insertBasecamp(me, spotId, "RECRUITING", START.plusDays(9));

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[*].basecampId")
                .isEqualTo(java.util.List.of((int) late, (int) sameDaySecond, (int) sameDayFirst, (int) early));
    }

    @Test
    @DisplayName("[F-13] size보다 한 건 더 있으면 hasNext가 true이고, 다음 페이지에서 나머지를 받는다")
    void pagesWithHasNext() {
        // given
        for (int day = 0; day < 3; day++) {
            fixture.insertBasecamp(me, spotId, "RECRUITING", START.plusDays(day));
        }

        // when
        MvcTestResult first = mvc.get().uri(MINE + "?size=2").cookie(session).exchange();
        MvcTestResult second =
                mvc.get().uri(MINE + "?size=2&page=1").cookie(session).exchange();
        MvcTestResult exact = mvc.get().uri(MINE + "?size=3").cookie(session).exchange();

        // then
        assertThat(first).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(first).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(second).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(second).bodyJson().extractingPath("$.page").isEqualTo(1);
        assertThat(second).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(exact).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-13] 허용 값이 아닌 relation은 fieldErrors와 함께 400 INVALID_INPUT이다")
    void invalidRelationIsRejected() {
        // when
        MvcTestResult result =
                mvc.get().uri(MINE + "?relation=OWNER").cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors").asList().isNotEmpty();
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않고 조회하면 401 AUTHENTICATION_REQUIRED이다")
    void anonymousIsUnauthorized() {
        // when
        MvcTestResult result = mvc.get().uri(MINE).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }
}
