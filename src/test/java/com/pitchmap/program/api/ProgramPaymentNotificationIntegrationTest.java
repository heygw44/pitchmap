package com.pitchmap.program.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import com.pitchmap.program.application.ProgramApplyFixture;
import com.pitchmap.program.application.ProgramPaymentExpiryService;
import jakarta.servlet.http.Cookie;
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
class ProgramPaymentNotificationIntegrationTest {

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
    private EmailVerificationService emailVerificationService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private ProgramPaymentExpiryService paymentExpiryService;

    private ProgramApplyFixture fixture;
    private Member applicant;
    private Cookie session;
    private long programId;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
        applicant = memberRepository.saveAndFlush(
                aMember().passwordHash(passwordEncoder.encode(PASSWORD)).build());
        session = login(applicant);
        programId = fixture.saveProgram(3, false);
    }

    @Test
    @DisplayName("[F-19][F-20] 결제하고 이벤트를 발행하면 신청자의 알림함에 행사 신청 확정 알림이 생긴다")
    void payNotifiesApplicantAfterPublish() {
        // given
        long applicationId = fixture.saveApplication(programId, applicant.getId(), "PENDING_PAYMENT");

        // when
        MvcTestResult paid = mvc.post()
                .uri("/api/program-applications/" + applicationId + "/pay")
                .header("Idempotency-Key", "pay-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"FAKE_CARD\"}")
                .with(csrf())
                .cookie(session)
                .exchange();
        outboxPublisher.publishPending();

        // then
        assertThat(paid).hasStatus(HttpStatus.OK);
        assertThat(paid).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        List<Map<String, Object>> notifications =
                jdbc.queryForList("SELECT member_id, type, title, link, read_at FROM notification");
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0))
                .containsEntry("member_id", applicant.getId())
                .containsEntry("type", "PROGRAM_APPLICATION_CONFIRMED")
                .containsEntry("title", "행사 신청 확정")
                .containsEntry("link", "/programs/" + programId)
                .containsEntry("read_at", null);
    }

    @Test
    @DisplayName("[F-19][F-20] 확정 신청을 취소하고 이벤트를 발행하면 환불을 알리는 취소 알림이 생긴다")
    void cancelNotifiesApplicantAfterPublish() {
        // given
        long applicationId = fixture.saveApplication(programId, applicant.getId(), "CONFIRMED");
        fixture.savePayment(applicationId, "PAID");

        // when
        MvcTestResult canceled = mvc.post()
                .uri("/api/program-applications/" + applicationId + "/cancel")
                .with(csrf())
                .cookie(session)
                .exchange();
        outboxPublisher.publishPending();

        // then
        assertThat(canceled).hasStatus(HttpStatus.OK);
        assertThat(canceled).bodyJson().extractingPath("$.refunded").isEqualTo(true);
        MvcTestResult inbox =
                mvc.get().uri("/api/me/notifications").cookie(session).exchange();
        assertThat(inbox).hasStatus(HttpStatus.OK);
        assertThat(inbox).bodyJson().extractingPath("$.content[0].type").isEqualTo("PROGRAM_APPLICATION_CANCELED");
        assertThat(inbox).bodyJson().extractingPath("$.content[0].body").isEqualTo("행사 신청을 취소했습니다. 결제한 금액은 환불됩니다.");
        assertThat(inbox).bodyJson().extractingPath("$.content[0].link").isEqualTo("/programs/" + programId);
    }

    @Test
    @DisplayName("[F-19][F-20][PG-05] 결제 기한이 지나 만료되고 이벤트를 발행하면 신청자의 알림함에 행사 신청 만료 알림이 1건 생긴다")
    void expiryNotifiesApplicantAfterPublish() {
        // given
        long applicationId = fixture.saveApplication(
                programId, applicant.getId(), "PENDING_PAYMENT", MutableClock.DEFAULT_INSTANT.minusSeconds(60));

        // when
        paymentExpiryService.run();
        outboxPublisher.publishPending();

        // then
        MvcTestResult inbox =
                mvc.get().uri("/api/me/notifications").cookie(session).exchange();
        assertThat(inbox).hasStatus(HttpStatus.OK);
        assertThat(inbox).bodyJson().extractingPath("$.content.length()").isEqualTo(1);
        assertThat(inbox).bodyJson().extractingPath("$.content[0].type").isEqualTo("PROGRAM_APPLICATION_EXPIRED");
        assertThat(inbox).bodyJson().extractingPath("$.content[0].title").isEqualTo("행사 신청 만료");
        assertThat(inbox).bodyJson().extractingPath("$.content[0].body").isEqualTo("결제 기한이 지나 행사 신청이 취소됐습니다.");
        assertThat(inbox).bodyJson().extractingPath("$.content[0].link").isEqualTo("/programs/" + programId);
        assertThat(fixture.applicationStatus(applicationId)).isEqualTo("EXPIRED");
    }

    private Cookie login(Member member) {
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + member.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")
                .with(csrf())
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie loggedIn = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(loggedIn).isNotNull();
        return verifyEmail(member, loggedIn);
    }

    // 쓰기 요청은 이메일 인증을 마친 회원만 보낼 수 있어서 인증 코드를 발급받아 인증한다.
    private Cookie verifyEmail(Member member, Cookie loggedIn) {
        String code = emailVerificationService
                .issueFor(member.getId(), "127.0.0.1")
                .orElseThrow()
                .code();
        MvcTestResult verified = mvc.post()
                .uri("/api/me/email-verification")
                .with(csrf())
                .cookie(loggedIn)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))
                .exchange();
        assertThat(verified).hasStatus(HttpStatus.OK);
        return loggedIn;
    }
}
