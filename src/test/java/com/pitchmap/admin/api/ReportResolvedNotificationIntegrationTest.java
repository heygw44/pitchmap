package com.pitchmap.admin.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import com.pitchmap.trust.application.CompanionReviewFixture;
import com.pitchmap.trust.application.MemberReportFixture;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
class ReportResolvedNotificationIntegrationTest {

    private static final String REPORTS = "/api/admin/member-reports";
    private static final String PASSWORD = "Valid-pass1";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OutboxPublisher outboxPublisher;

    private MemberReportFixture reportFixture;
    private Cookie adminSession;
    private long reporterId;
    private long targetId;
    private long reportId;

    @BeforeEach
    void setUp() {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        reportFixture = new MemberReportFixture(jdbc);
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", adminId);
        adminSession = login(adminId);
        long basecampId = fixture.saveBasecamp("COMPLETED", BASE);
        reporterId = fixture.saveVerifiedMember(TestSequence.nickname());
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
        reportId = reportFixture.insertMemberReport(reporterId, targetId, basecampId, "NO_SHOW", "IN_REVIEW", BASE);
    }

    @Test
    @DisplayName("[F-20][F-21] 신고를 기각하고 이벤트를 발행하면 신고자만 알림을 받고 본문에 제재 내용이 없다")
    void dismissNotifiesReporterWithoutSanction() {
        // when
        MvcTestResult dismissed = post(REPORTS + "/" + reportId + "/dismiss", null);
        outboxPublisher.publishPending();

        // then
        assertThat(dismissed).hasStatus(HttpStatus.OK);
        List<Map<String, Object>> notifications =
                jdbc.queryForList("SELECT member_id, type, title, body, link FROM notification");
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0))
                .containsEntry("member_id", reporterId)
                .containsEntry("type", "MEMBER_REPORT_RESOLVED")
                .containsEntry("title", "신고 처리 완료")
                .containsEntry("body", "신고하신 내용을 검토했지만 조치 대상이 아니라고 판단했습니다.")
                .containsEntry("link", null);
    }

    @Test
    @DisplayName("[F-20][F-21] 경고로 조치하면 신고자는 제재 내용 없는 결과만, 신고 대상은 경고 알림을 받는다")
    void actionNotifiesReporterWithoutSanctionAndTargetWithSanction() {
        // when
        MvcTestResult actioned =
                post(REPORTS + "/" + reportId + "/action", "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"사유\"}}");
        outboxPublisher.publishPending();

        // then
        assertThat(actioned).hasStatus(HttpStatus.OK);
        String reporterBody =
                jdbc.queryForObject("SELECT body FROM notification WHERE member_id = ?", String.class, reporterId);
        assertThat(reporterBody).isEqualTo("신고하신 내용을 검토해 조치했습니다.").doesNotContain("경고", "제재", "정지");
        assertThat(jdbc.queryForObject("SELECT body FROM notification WHERE member_id = ?", String.class, targetId))
                .isEqualTo("경고를 받았습니다.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class))
                .isEqualTo(2);
    }

    private MvcTestResult post(String uri, String body) {
        var request = mvc.post().uri(uri).cookie(adminSession).with(csrf());
        if (body == null) {
            return request.exchange();
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private Cookie login(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }
}
