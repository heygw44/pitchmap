package com.pitchmap.admin.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.admin.application.AuditLogPage;
import com.pitchmap.admin.application.AuditLogQueryService;
import com.pitchmap.admin.application.AuditLogView;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.trust.application.AdminMemberReportDetail;
import com.pitchmap.trust.application.AdminMemberReportPage;
import com.pitchmap.trust.application.AdminMemberReportQueryService;
import com.pitchmap.trust.application.AdminMemberReportSummary;
import com.pitchmap.trust.application.MemberReportActionCommand;
import com.pitchmap.trust.application.MemberReportActionResult;
import com.pitchmap.trust.application.MemberReportActionService;
import com.pitchmap.trust.application.MemberReportDismissService;
import com.pitchmap.trust.application.MemberReportReviewService;
import com.pitchmap.trust.application.MemberReportStatusResult;
import com.pitchmap.trust.application.SanctionLiftResult;
import com.pitchmap.trust.application.SanctionLiftService;
import com.pitchmap.trust.domain.TrustErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest({AdminMemberReportController.class, AdminSanctionController.class, AuditLogController.class})
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class AdminControllersTest {

    private static final String REPORTS = "/api/admin/member-reports";
    private static final String SANCTIONS = "/api/admin/sanctions";
    private static final String AUDIT_LOGS = "/api/admin/audit-logs";
    private static final Instant AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final long ADMIN_ID = 1L;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private AdminMemberReportQueryService queryService;

    @MockitoBean
    private MemberReportReviewService reviewService;

    @MockitoBean
    private MemberReportActionService actionService;

    @MockitoBean
    private MemberReportDismissService dismissService;

    @MockitoBean
    private SanctionLiftService sanctionLiftService;

    @MockitoBean
    private AuditLogQueryService auditLogQueryService;

    @Test
    @DisplayName("[F-21] 로그인하지 않고 관리자 API를 부르면 401 AUTHENTICATION_REQUIRED이다")
    void anonymousIsUnauthorized() {
        MvcTestResult list = mvc.get().uri(REPORTS).exchange();
        MvcTestResult lift = mvc.post().uri(SANCTIONS + "/3/lift").with(csrf()).exchange();
        MvcTestResult audit = mvc.get().uri(AUDIT_LOGS).exchange();

        for (MvcTestResult result : List.of(list, lift, audit)) {
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        }
        verifyNoInteractions(queryService, sanctionLiftService, auditLogQueryService);
    }

    @Test
    @DisplayName("[F-21] 관리자가 아닌 회원이 관리자 API를 부르면 403 ACCESS_DENIED이다")
    void userIsForbidden() {
        MvcTestResult list = mvc.get().uri(REPORTS).with(member("USER")).exchange();
        MvcTestResult detail =
                mvc.get().uri(REPORTS + "/5").with(member("USER")).exchange();
        MvcTestResult start = post(member("USER"), REPORTS + "/5/start-review", null);
        MvcTestResult action = post(member("USER"), REPORTS + "/5/action", "{\"hideReview\":true}");
        MvcTestResult dismiss = post(member("USER"), REPORTS + "/5/dismiss", null);
        MvcTestResult lift = post(member("USER"), SANCTIONS + "/3/lift", null);
        MvcTestResult audit = mvc.get().uri(AUDIT_LOGS).with(member("USER")).exchange();

        for (MvcTestResult result : List.of(list, detail, start, action, dismiss, lift, audit)) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        }
        verifyNoInteractions(
                queryService, reviewService, actionService, dismissService, sanctionLiftService, auditLogQueryService);
    }

    @Test
    @DisplayName("[F-21] 관리자라도 변경 요청에 CSRF 토큰이 없으면 403 ACCESS_DENIED이다")
    void adminWithoutCsrfIsForbidden() {
        MvcTestResult result =
                mvc.post().uri(REPORTS + "/5/start-review").with(admin()).exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("[F-21] 신고 목록은 신고자와 대상을 묶어 응답하고 신고 내용은 담지 않는다")
    void listReturnsSummaries() {
        when(queryService.list(eq("IN_REVIEW"), eq(true), anyInt(), anyInt()))
                .thenReturn(new AdminMemberReportPage(List.of(summary()), 0, 20, false));

        MvcTestResult result = mvc.get()
                .uri(REPORTS)
                .param("status", "IN_REVIEW")
                .param("urgent", "true")
                .with(admin())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].reportId").isEqualTo(5);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reporter.memberId")
                .isEqualTo(10);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].target.nickname")
                .isEqualTo("대상");
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].content");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        verify(queryService).list("IN_REVIEW", true, 0, 20);
    }

    @Test
    @DisplayName("[F-21] 신고 목록의 size가 1~50을 벗어나거나 page가 음수이면 400 INVALID_INPUT이다")
    void listRejectsInvalidPaging() {
        for (String[] param : new String[][] {{"size", "0"}, {"size", "51"}, {"page", "-1"}}) {
            MvcTestResult result = mvc.get()
                    .uri(REPORTS)
                    .param(param[0], param[1])
                    .with(admin())
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("[F-21] 신고 상세는 회원 신고이면 companionReview가 null이고 제재 이력을 담는다")
    void detailReturnsHistory() {
        AdminMemberReportDetail detail = new AdminMemberReportDetail(
                summary(),
                "신고 내용",
                null,
                null,
                null,
                new AdminMemberReportDetail.Basecamp(3L, "굴업도 1박", "COMPLETED", LocalDate.parse("2026-09-20")),
                null,
                List.of(new AdminMemberReportDetail.SanctionHistory(8L, "WARNING", 1, "ACTIVE", "욕설", AT, null, null)));
        when(queryService.detail(5L)).thenReturn(detail);

        MvcTestResult result = mvc.get().uri(REPORTS + "/5").with(admin()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content").isEqualTo("신고 내용");
        assertThat(result).bodyJson().extractingPath("$.basecamp.title").isEqualTo("굴업도 1박");
        assertThat(result).bodyJson().extractingPath("$.companionReview").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.sanctionHistory[0].type")
                .isEqualTo("WARNING");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.sanctionHistory[0].level")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[F-21] 없는 신고의 상세는 404 NOT_FOUND이다")
    void detailOfMissingReportIsNotFound() {
        when(queryService.detail(99L))
                .thenThrow(new BusinessException(com.pitchmap.common.error.CommonErrorCode.NOT_FOUND));

        MvcTestResult result = mvc.get().uri(REPORTS + "/99").with(admin()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[SN-04] 검토 시작은 로그인한 관리자 ID로 서비스를 부르고 신고 ID와 상태를 응답한다")
    void startReviewPassesAdminId() {
        when(reviewService.startReview(5L, ADMIN_ID)).thenReturn(new MemberReportStatusResult(5L, "IN_REVIEW"));

        MvcTestResult result = post(admin(), REPORTS + "/5/start-review", null);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.reportId").isEqualTo(5);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("IN_REVIEW");
    }

    @Test
    @DisplayName("[SN-04] 검토를 시작할 수 없는 상태이면 409 REPORT_INVALID_STATE로 응답한다")
    void startReviewInvalidState() {
        when(reviewService.startReview(5L, ADMIN_ID))
                .thenThrow(new BusinessException(TrustErrorCode.REPORT_INVALID_STATE));

        MvcTestResult result = post(admin(), REPORTS + "/5/start-review", null);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("REPORT_INVALID_STATE");
    }

    @Test
    @DisplayName("[SN-04] 조치 요청은 제재와 후기 숨김, 메모를 서비스에 넘기고 신고 ID, 상태, 제재 ID를 응답한다")
    void actionPassesCommand() {
        when(actionService.act(any(MemberReportActionCommand.class)))
                .thenReturn(new MemberReportActionResult(5L, "ACTIONED", 8L, 11L, true));

        MvcTestResult result = post(
                admin(),
                REPORTS + "/5/action",
                "{\"sanction\":{\"type\":\"SUSPEND_7D\",\"reason\":\"반복 위반\"},\"hideReview\":false,\"note\":\"메모\"}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.sanctionId").isEqualTo(8);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIONED");
        assertThat(result).bodyJson().doesNotHavePath("$.targetMemberId");
        verify(actionService).act(new MemberReportActionCommand(5L, ADMIN_ID, "SUSPEND_7D", "반복 위반", false, "메모"));
    }

    @Test
    @DisplayName("[SN-07] 제재 없이 후기만 숨기는 조치 요청도 받고 sanctionId는 null이다")
    void actionWithoutSanction() {
        when(actionService.act(any(MemberReportActionCommand.class)))
                .thenReturn(new MemberReportActionResult(5L, "ACTIONED", null, 11L, false));

        MvcTestResult result = post(admin(), REPORTS + "/5/action", "{\"sanction\":null,\"hideReview\":true}");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.sanctionId").isNull();
        verify(actionService).act(new MemberReportActionCommand(5L, ADMIN_ID, null, null, true, null));
    }

    @Test
    @DisplayName("[SN-04] 조치 요청에서 hideReview를 생략하면 false로 서비스에 넘긴다")
    void actionDefaultsHideReviewToFalse() {
        when(actionService.act(any(MemberReportActionCommand.class)))
                .thenReturn(new MemberReportActionResult(5L, "ACTIONED", 8L, 11L, false));

        MvcTestResult result =
                post(admin(), REPORTS + "/5/action", "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"욕설\"}}");

        assertThat(result).hasStatus(HttpStatus.OK);
        verify(actionService).act(new MemberReportActionCommand(5L, ADMIN_ID, "WARNING", "욕설", false, null));
    }

    @Test
    @DisplayName("[SN-04] 조치 요청의 사유가 비었거나 500자를 넘고 메모가 1000자를 넘으면 400 INVALID_INPUT이다")
    void actionRejectsInvalidBody() {
        String blankReason = "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\" \"},\"hideReview\":false}";
        String longReason = "{\"sanction\":{\"type\":\"WARNING\",\"reason\":\"" + "가".repeat(501) + "\"}}";
        String missingType = "{\"sanction\":{\"reason\":\"사유\"}}";
        String longNote = "{\"hideReview\":true,\"note\":\"" + "가".repeat(1001) + "\"}";

        for (String body : List.of(blankReason, longReason, missingType, longNote)) {
            MvcTestResult result = post(admin(), REPORTS + "/5/action", body);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(actionService);
    }

    @Test
    @DisplayName("[SN-04] 조치 요청 본문이 없으면 400 INVALID_INPUT이다")
    void actionRequiresBody() {
        MvcTestResult result = post(admin(), REPORTS + "/5/action", null);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(actionService);
    }

    @Test
    @DisplayName("[SN-04] 서비스가 던진 REPORT_INVALID_STATE는 409로 응답한다")
    void actionInvalidStateIsConflict() {
        when(actionService.act(any(MemberReportActionCommand.class)))
                .thenThrow(new BusinessException(TrustErrorCode.REPORT_INVALID_STATE));

        MvcTestResult result = post(admin(), REPORTS + "/5/action", "{\"hideReview\":true}");

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("REPORT_INVALID_STATE");
    }

    @Test
    @DisplayName("[SN-05] 기각은 본문을 생략해도 되고, 메모가 있으면 서비스에 넘긴다")
    void dismissAcceptsOptionalBody() {
        when(dismissService.dismiss(anyLong(), anyLong(), any()))
                .thenReturn(new MemberReportStatusResult(5L, "DISMISSED"));

        MvcTestResult withoutBody = post(admin(), REPORTS + "/5/dismiss", null);
        MvcTestResult withNote = post(admin(), REPORTS + "/5/dismiss", "{\"note\":\"근거 없음\"}");

        assertThat(withoutBody).hasStatus(HttpStatus.OK);
        assertThat(withNote).hasStatus(HttpStatus.OK);
        assertThat(withNote).bodyJson().extractingPath("$.status").isEqualTo("DISMISSED");
        verify(dismissService).dismiss(5L, ADMIN_ID, null);
        verify(dismissService).dismiss(5L, ADMIN_ID, "근거 없음");
    }

    @Test
    @DisplayName("[SN-05] 기각 메모가 1000자를 넘으면 400 INVALID_INPUT이다")
    void dismissRejectsLongNote() {
        MvcTestResult result = post(admin(), REPORTS + "/5/dismiss", "{\"note\":\"" + "가".repeat(1001) + "\"}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(dismissService);
    }

    @Test
    @DisplayName("[SN-15] 제재 해제는 제재 ID와 상태를 응답하고, 적용 중이 아니면 409 SANCTION_INVALID_STATE이다")
    void liftReturnsStatusOrConflict() {
        when(sanctionLiftService.lift(3L, ADMIN_ID)).thenReturn(new SanctionLiftResult(3L, "LIFTED"));
        when(sanctionLiftService.lift(4L, ADMIN_ID))
                .thenThrow(new BusinessException(TrustErrorCode.SANCTION_INVALID_STATE));

        MvcTestResult lifted = post(admin(), SANCTIONS + "/3/lift", null);
        MvcTestResult conflict = post(admin(), SANCTIONS + "/4/lift", null);

        assertThat(lifted).hasStatus(HttpStatus.OK);
        assertThat(lifted).bodyJson().extractingPath("$.status").isEqualTo("LIFTED");
        assertThat(conflict).hasStatus(HttpStatus.CONFLICT);
        assertThat(conflict).bodyJson().extractingPath("$.code").isEqualTo("SANCTION_INVALID_STATE");
    }

    @Test
    @DisplayName("[F-21] 감사 로그 조회는 필터를 서비스에 넘기고 detail을 JSON 객체로 응답한다")
    void auditLogsReturnDetailAsObject() {
        AuditLogView view = new AuditLogView(
                1L, ADMIN_ID, "REPORT_ACTION", "MEMBER_REPORT", 5L, jsonMapper.readTree("{\"hideReview\":true}"), AT);
        when(auditLogQueryService.findLogs(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new AuditLogPage(List.of(view), 0, 20, true));

        MvcTestResult result = mvc.get()
                .uri(AUDIT_LOGS)
                .param("adminId", "1")
                .param("targetType", "MEMBER_REPORT")
                .param("from", "2026-10-05T00:00:00Z")
                .param("to", "2026-10-06T00:00:00Z")
                .with(admin())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].auditLogId").isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].detail.hideReview")
                .isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        verify(auditLogQueryService)
                .findLogs(
                        1L,
                        "MEMBER_REPORT",
                        Instant.parse("2026-10-05T00:00:00Z"),
                        Instant.parse("2026-10-06T00:00:00Z"),
                        0,
                        20);
    }

    @Test
    @DisplayName("[F-21] 감사 로그 조회의 시각 형식이 틀리거나 size가 1~50을 벗어나면 400 INVALID_INPUT이다")
    void auditLogsRejectInvalidParameters() {
        for (String[] param : new String[][] {{"from", "어제"}, {"to", "2026-13-40"}, {"size", "51"}, {"size", "0"}}) {
            MvcTestResult result = mvc.get()
                    .uri(AUDIT_LOGS)
                    .param(param[0], param[1])
                    .with(admin())
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        verifyNoInteractions(auditLogQueryService);
    }

    private static AdminMemberReportSummary summary() {
        return new AdminMemberReportSummary(
                5L, "MEMBER", "HARASSMENT_OR_THREAT", true, "IN_REVIEW", 10L, "신고자", 11L, "대상", 3L, AT);
    }

    private MvcTestResult post(RequestPostProcessor login, String path, String body) {
        var request = mvc.post().uri(path).with(login).with(csrf());
        if (body == null) {
            return request.exchange();
        }
        return request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static RequestPostProcessor admin() {
        return member("ADMIN");
    }

    // 로그인 서비스가 세션에 넣는 것과 같은 모양의 인증 정보를 만든다. 역할 권한과 이메일 인증 권한을 함께 붙인다.
    private static RequestPostProcessor member(String role) {
        LoginMember loginMember = new LoginMember(ADMIN_ID, role, true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role),
                        new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED))));
    }
}
