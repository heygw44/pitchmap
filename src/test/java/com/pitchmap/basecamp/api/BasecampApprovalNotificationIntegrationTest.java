package com.pitchmap.basecamp.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
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
class BasecampApprovalNotificationIntegrationTest {

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
    private OutboxPublisher outboxPublisher;

    private BasecampApiFixture fixture;
    private Member leader;
    private Cookie leaderSession;
    private Member applicant;
    private Cookie applicantSession;
    private long basecampId;

    @BeforeEach
    void setUp() {
        fixture = new BasecampApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        leader = fixture.saveMember();
        leaderSession = fixture.identityVerifiedSession(leader, "FEMALE");
        applicant = fixture.saveMember();
        applicantSession = fixture.identityVerifiedSession(applicant, "MALE");
        long spotId = fixture.insertSpot("개머리언덕", 37.25, 127.25);
        basecampId = fixture.insertBasecamp(leader, spotId, "RECRUITING", BasecampApiFixture.DEFAULT_START_DATE);
    }

    @Test
    @DisplayName("[F-20][F-13] 캠프 리더가 승인하고 이벤트를 발행하면 신청자의 알림함에 합류 승인 알림이 생기고 캠프 리더에게는 생기지 않는다")
    void approvalNotifiesApplicantAfterPublish() {
        // given
        fixture.insertApplicationRow(basecampId, applicant, "PENDING");
        long applicationId = jdbc.queryForObject("SELECT MAX(id) FROM basecamp_application", Long.class);

        // when
        MvcTestResult approved = mvc.post()
                .uri("/api/basecamps/" + basecampId + "/applications/" + applicationId + "/approve")
                .with(csrf())
                .cookie(leaderSession)
                .exchange();
        outboxPublisher.publishPending();

        // then
        assertThat(approved).hasStatus(HttpStatus.OK);
        List<Map<String, Object>> notifications =
                jdbc.queryForList("SELECT member_id, type, title, link, read_at FROM notification");
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0))
                .containsEntry("member_id", applicant.getId())
                .containsEntry("type", "BASECAMP_APPROVED")
                .containsEntry("title", "합류 승인")
                .containsEntry("link", "/basecamps/" + basecampId)
                .containsEntry("read_at", null);
        MvcTestResult inbox =
                mvc.get().uri("/api/me/notifications").cookie(applicantSession).exchange();
        assertThat(inbox).hasStatus(HttpStatus.OK);
        assertThat(inbox).bodyJson().extractingPath("$.content[0].type").isEqualTo("BASECAMP_APPROVED");
        MvcTestResult leaderInbox =
                mvc.get().uri("/api/me/notifications").cookie(leaderSession).exchange();
        assertThat(leaderInbox).bodyJson().extractingPath("$.content").asArray().isEmpty();
    }
}
