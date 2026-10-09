package com.pitchmap.program.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.program.application.ProgramApplyFixture;
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
class MyProgramApplicationApiIntegrationTest {

    private static final String MINE = "/api/me/program-applications";
    private static final String PASSWORD = "Valid-pass1";
    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;
    private Member me;
    private Member other;
    private Cookie session;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
        me = saveLoginMember();
        other = saveLoginMember();
        session = login(me);
    }

    @Test
    @DisplayName("[F-18] 이메일 인증 전의 회원도 자기 신청만 최근 신청부터 받고, 다른 회원의 신청과 이메일은 응답에 없다")
    void returnsOnlyOwnApplicationsNewestFirst() throws Exception {
        // given
        long first = fixture.saveProgram(10, false);
        long second = fixture.saveProgram(10, false);
        long firstApplication = fixture.saveApplication(first, me.getId(), "PENDING_PAYMENT");
        fixture.saveApplication(first, other.getId(), "PENDING_PAYMENT");
        long secondApplication = fixture.saveApplication(second, me.getId(), "PENDING_PAYMENT");
        fixture.saveApplication(second, other.getId(), "PENDING_PAYMENT");

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].applicationId")
                .isEqualTo((int) secondApplication);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].program.programId")
                .isEqualTo((int) second);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[1].applicationId")
                .isEqualTo((int) firstApplication);
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(20);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain(me.getEmail())
                .doesNotContain(other.getEmail())
                .doesNotContain("email");
    }

    @Test
    @DisplayName("[F-18] 항목에는 신청 정보와 행사 요약이 담기고 결제 대기 신청은 확정·취소 시각과 사유가 null이다")
    void itemShapeOfPendingApplication() {
        // given
        long programId = fixture.saveProgram(10, false);
        long applicationId = fixture.saveApplication(programId, me.getId(), "PENDING_PAYMENT");

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].applicationId")
                .isEqualTo((int) applicationId);
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("PENDING_PAYMENT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].paymentDueAt")
                .isEqualTo("2026-10-05T03:15:00Z");
        assertThat(result).bodyJson().extractingPath("$.content[0].confirmedAt").isNull();
        assertThat(result).bodyJson().extractingPath("$.content[0].canceledAt").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].cancelReason")
                .isNull();
        assertThat(result).bodyJson().extractingPath("$.content[0].createdAt").isEqualTo("2026-10-05T03:00:00Z");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].program.title")
                .isEqualTo("가을 백패킹");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].program.locationText")
                .isEqualTo("설악산 입구");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].program.startAt")
                .isEqualTo("2026-10-12T03:00:00Z");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].program.endAt")
                .isEqualTo("2026-10-13T03:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.content[0].program.fee").isEqualTo(30000);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].program.status")
                .isEqualTo("OPEN");
    }

    @Test
    @DisplayName("[F-18] 확정 신청은 confirmedAt, 취소 신청은 canceledAt과 사유, 만료 신청은 만료 사유를 담고 결제 기한은 그대로 둔다")
    void fieldsPerStatus() {
        // given
        long confirmedProgram = fixture.saveProgram(10, false);
        long canceledProgram = fixture.saveProgram(10, false);
        long expiredProgram = fixture.saveProgram(10, false);
        long confirmed = fixture.saveApplication(confirmedProgram, me.getId(), "CONFIRMED");
        jdbc.update(
                "UPDATE program_application SET confirmed_at = ? WHERE id = ?", utc(NOW.plusSeconds(60)), confirmed);
        long canceled = fixture.saveApplication(canceledProgram, me.getId(), "CANCELED");
        jdbc.update(
                "UPDATE program_application SET canceled_at = ?, cancel_reason = 'USER' WHERE id = ?",
                utc(NOW.plusSeconds(120)),
                canceled);
        long expired = fixture.saveApplication(expiredProgram, me.getId(), "EXPIRED");
        jdbc.update(
                "UPDATE program_application SET canceled_at = ?, cancel_reason = 'EXPIRED' WHERE id = ?",
                utc(NOW.plusSeconds(900)),
                expired);

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then: 최근 신청부터 만료, 취소, 확정 순서다.
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("EXPIRED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].cancelReason")
                .isEqualTo("EXPIRED");
        assertThat(result).bodyJson().extractingPath("$.content[0].canceledAt").isEqualTo("2026-10-05T03:15:00Z");
        assertThat(result).bodyJson().extractingPath("$.content[0].confirmedAt").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].paymentDueAt")
                .isEqualTo("2026-10-05T03:15:00Z");
        assertThat(result).bodyJson().extractingPath("$.content[1].status").isEqualTo("CANCELED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[1].cancelReason")
                .isEqualTo("USER");
        assertThat(result).bodyJson().extractingPath("$.content[1].canceledAt").isEqualTo("2026-10-05T03:02:00Z");
        assertThat(result).bodyJson().extractingPath("$.content[2].status").isEqualTo("CONFIRMED");
        assertThat(result).bodyJson().extractingPath("$.content[2].confirmedAt").isEqualTo("2026-10-05T03:01:00Z");
        assertThat(result).bodyJson().extractingPath("$.content[2].canceledAt").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[2].cancelReason")
                .isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[2].paymentDueAt")
                .isEqualTo("2026-10-05T03:15:00Z");
    }

    @Test
    @DisplayName("[F-18] status를 보내면 그 상태의 신청만 준다")
    void filtersByStatus() {
        // given
        long pendingProgram = fixture.saveProgram(10, false);
        long canceledProgram = fixture.saveProgram(10, false);
        fixture.saveApplication(pendingProgram, me.getId(), "PENDING_PAYMENT");
        long canceled = fixture.saveApplication(canceledProgram, me.getId(), "CANCELED");

        // when
        MvcTestResult result =
                mvc.get().uri(MINE).param("status", "CANCELED").cookie(session).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].applicationId")
                .isEqualTo((int) canceled);
    }

    @Test
    @DisplayName("[F-18] size보다 신청이 많으면 hasNext가 true이고, 다음 페이지에 나머지를 준다")
    void paging() {
        // given
        long programId = fixture.saveProgram(10, false);
        long oldest = fixture.saveApplication(programId, me.getId(), "CANCELED");
        long middle = fixture.saveApplication(programId, me.getId(), "CANCELED");
        long newest = fixture.saveApplication(programId, me.getId(), "CANCELED");

        // when
        MvcTestResult firstPage = mvc.get()
                .uri(MINE)
                .param("size", "2")
                .param("page", "0")
                .cookie(session)
                .exchange();
        MvcTestResult secondPage = mvc.get()
                .uri(MINE)
                .param("size", "2")
                .param("page", "1")
                .cookie(session)
                .exchange();

        // then
        assertThat(firstPage).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(firstPage)
                .bodyJson()
                .extractingPath("$.content[0].applicationId")
                .isEqualTo((int) newest);
        assertThat(firstPage)
                .bodyJson()
                .extractingPath("$.content[1].applicationId")
                .isEqualTo((int) middle);
        assertThat(firstPage).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(secondPage).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(secondPage)
                .bodyJson()
                .extractingPath("$.content[0].applicationId")
                .isEqualTo((int) oldest);
        assertThat(secondPage).bodyJson().extractingPath("$.page").isEqualTo(1);
        assertThat(secondPage).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-18] 취소된 행사의 신청도 내 기록으로 나오고 program.status는 CANCELED이다. 신청 시작 전·마감 뒤 행사는 UPCOMING·CLOSED이다")
    void programStatusIsComputedLikePublicList() {
        // given
        long canceledProgram = fixture.saveProgram(
                10, false, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(5)), "CANCELED");
        long upcomingProgram =
                fixture.saveProgram(10, false, NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(5)), "SCHEDULED");
        long closedProgram = fixture.saveProgram(
                10, false, NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofHours(1)), "SCHEDULED");
        fixture.saveApplication(canceledProgram, me.getId(), "CANCELED");
        fixture.saveApplication(upcomingProgram, me.getId(), "PENDING_PAYMENT");
        fixture.saveApplication(closedProgram, me.getId(), "CONFIRMED");

        // when
        MvcTestResult result = mvc.get().uri(MINE).cookie(session).exchange();

        // then: 최근 신청부터 마감 뒤, 신청 시작 전, 취소 순서다.
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].program.status")
                .isEqualTo("CLOSED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[1].program.status")
                .isEqualTo("UPCOMING");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[2].program.status")
                .isEqualTo("CANCELED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[2].program.programId")
                .isEqualTo((int) canceledProgram);
    }

    @Test
    @DisplayName("[F-18] 신청이 없으면 빈 목록이고, 로그인하지 않으면 401이다")
    void emptyAndUnauthenticated() {
        MvcTestResult empty = mvc.get().uri(MINE).cookie(session).exchange();
        MvcTestResult anonymous = mvc.get().uri(MINE).exchange();

        assertThat(empty).hasStatus(HttpStatus.OK);
        assertThat(empty).bodyJson().extractingPath("$.content").asList().isEmpty();
        assertThat(empty).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    private Member saveLoginMember() {
        return memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build());
    }

    private Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(member.getEmail(), PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
