package com.pitchmap.publicdata.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.publicdata.application.SyncJobLauncher;
import com.pitchmap.publicdata.application.SyncJobRunPage;
import com.pitchmap.publicdata.application.SyncJobRunQueryService;
import com.pitchmap.publicdata.application.SyncJobRunView;
import com.pitchmap.publicdata.domain.PublicDataErrorCode;
import com.pitchmap.publicdata.domain.SyncJobStatus;
import com.pitchmap.publicdata.domain.SyncJobType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

@WebMvcTest(SyncJobAdminController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class SyncJobAdminControllerTest {

    private static final String PATH = "/api/admin/sync-jobs";
    private static final Instant STARTED_AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-10-05T03:05:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SyncJobLauncher syncJobLauncher;

    @MockitoBean
    private SyncJobRunQueryService syncJobRunQueryService;

    @Test
    @DisplayName("[F-06] 관리자가 작업 실행을 요청하면 202와 실행 기록 ID를 응답하고 요청한 종류로 작업을 시작한다")
    void adminLaunchReturnsAcceptedWithJobRunId() {
        // given
        when(syncJobLauncher.launch(SyncJobType.GOCAMPING)).thenReturn(7L);

        // when
        MvcTestResult result = launch(admin(), "{\"jobType\":\"GOCAMPING\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).bodyJson().extractingPath("$.jobRunId").isEqualTo(7);
        verify(syncJobLauncher).launch(SyncJobType.GOCAMPING);
    }

    @Test
    @DisplayName("[F-06] 로그인하지 않고 작업 실행을 요청하면 401 AUTHENTICATION_REQUIRED로 응답한다")
    void anonymousLaunchIsUnauthorized() {
        // when
        MvcTestResult result = mvc.post()
                .uri(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobType\":\"GOCAMPING\"}")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(syncJobLauncher);
    }

    @Test
    @DisplayName("[F-06] 관리자가 아닌 회원이 작업 실행을 요청하면 403 ACCESS_DENIED로 응답한다")
    void userLaunchIsForbidden() {
        // when
        MvcTestResult result = launch(member("USER"), "{\"jobType\":\"GOCAMPING\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        verifyNoInteractions(syncJobLauncher);
    }

    @Test
    @DisplayName("[F-06] 관리자가 아닌 회원이 실행 기록을 조회하면 403 ACCESS_DENIED로 응답한다")
    void userListIsForbidden() {
        // when
        MvcTestResult result = mvc.get().uri(PATH).with(member("USER")).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        verifyNoInteractions(syncJobRunQueryService);
    }

    @Test
    @DisplayName("[F-06] 관리자라도 작업 실행 요청에 CSRF 토큰이 없으면 403 ACCESS_DENIED로 응답한다")
    void launchWithoutCsrfTokenIsForbidden() {
        // when
        MvcTestResult result = mvc.post()
                .uri(PATH)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobType\":\"GOCAMPING\"}")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        verifyNoInteractions(syncJobLauncher);
    }

    @Test
    @DisplayName("[F-06] 같은 종류의 작업이 실행 중이면 409 SYNC_JOB_ALREADY_RUNNING으로 응답한다")
    void alreadyRunningReturnsConflict() {
        // given
        when(syncJobLauncher.launch(SyncJobType.FOREST))
                .thenThrow(new BusinessException(PublicDataErrorCode.SYNC_JOB_ALREADY_RUNNING));

        // when
        MvcTestResult result = launch(admin(), "{\"jobType\":\"FOREST\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SYNC_JOB_ALREADY_RUNNING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"jobType\":\"UNKNOWN\"}", "{\"jobType\":\"gocamping\"}", "not-json"})
    @DisplayName("[F-06] 작업 종류를 읽을 수 없으면 400 INVALID_INPUT으로 응답하고 작업을 시작하지 않는다")
    void unreadableJobTypeReturnsInvalidInput(String body) {
        // when
        MvcTestResult result = launch(admin(), body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(syncJobLauncher);
    }

    @Test
    @DisplayName("[F-06] 작업 종류가 없으면 jobType 필드 오류로 400을 응답하고 작업을 시작하지 않는다")
    void missingJobTypeReturnsFieldError() {
        // when
        MvcTestResult result = launch(admin(), "{}");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='jobType')]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(syncJobLauncher);
    }

    @Test
    @DisplayName("[F-06] 관리자가 실행 기록을 조회하면 목록 형식으로 각 실행의 종류, 상태, 건수, 오류, 시각을 응답한다")
    void listReturnsRunsInPageEnvelope() {
        // given
        SyncJobRunView failed = new SyncJobRunView(
                9L,
                SyncJobType.FOREST,
                SyncJobStatus.FAILED,
                3,
                1,
                "UncheckedIOException: 파일 없음",
                STARTED_AT,
                FINISHED_AT);
        SyncJobRunView running =
                new SyncJobRunView(8L, SyncJobType.FOREST, SyncJobStatus.RUNNING, 0, 0, null, STARTED_AT, null);
        when(syncJobRunQueryService.findRuns(SyncJobType.FOREST, 1, 2))
                .thenReturn(new SyncJobRunPage(List.of(failed, running), 1, 2, true));

        // when
        MvcTestResult result = mvc.get()
                .uri(PATH)
                .param("jobType", "FOREST")
                .param("page", "1")
                .param("size", "2")
                .with(admin())
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(result).bodyJson().extractingPath("$.content[0].jobRunId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.content[0].jobType").isEqualTo("FOREST");
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].processedCount")
                .isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].skippedCount")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].errorMessage")
                .isEqualTo("UncheckedIOException: 파일 없음");
        assertThat(result).bodyJson().extractingPath("$.content[0].startedAt").isEqualTo("2026-10-05T03:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.content[0].finishedAt").isEqualTo("2026-10-05T03:05:00Z");
        assertThat(result).bodyJson().extractingPath("$.content[1].jobRunId").isEqualTo(8);
        assertThat(result).bodyJson().extractingPath("$.content[1].status").isEqualTo("RUNNING");
        assertThat(result).bodyJson().extractingPath("$.content[1].finishedAt").isNull();
    }

    @Test
    @DisplayName("[F-06] 조회 조건을 주지 않으면 모든 종류를 첫 페이지부터 20개씩 조회한다")
    void listUsesDefaultsWithoutParameters() {
        // given
        when(syncJobRunQueryService.findRuns(null, 0, 20)).thenReturn(new SyncJobRunPage(List.of(), 0, 20, false));

        // when
        MvcTestResult result = mvc.get().uri(PATH).with(admin()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content").asList().isEmpty();
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        verify(syncJobRunQueryService).findRuns(null, 0, 20);
    }

    @Test
    @DisplayName("[F-06] 페이지 크기는 50까지 받는다")
    void listAcceptsMaximumPageSize() {
        // given
        when(syncJobRunQueryService.findRuns(any(), anyInt(), anyInt()))
                .thenReturn(new SyncJobRunPage(List.of(), 0, 50, false));

        // when
        MvcTestResult result =
                mvc.get().uri(PATH).param("size", "50").with(admin()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(syncJobRunQueryService).findRuns(null, 0, 50);
    }

    @ParameterizedTest
    @CsvSource({"page, -1", "size, 0", "size, 51", "jobType, UNKNOWN", "page, abc"})
    @DisplayName("[F-06] 페이지 번호가 음수이거나 크기가 1~50 밖이거나 종류를 읽을 수 없으면 400 INVALID_INPUT으로 응답한다")
    void listRejectsInvalidParameters(String name, String value) {
        // when
        MvcTestResult result =
                mvc.get().uri(PATH).param(name, value).with(admin()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='" + name + "')]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(syncJobRunQueryService);
    }

    private MvcTestResult launch(RequestPostProcessor login, String body) {
        return mvc.post()
                .uri(PATH)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static RequestPostProcessor admin() {
        return member("ADMIN");
    }

    // 로그인 서비스가 세션에 넣는 것과 같은 모양의 인증 정보를 만든다. 역할 권한과 이메일 인증 권한을 함께 붙인다.
    private static RequestPostProcessor member(String role) {
        LoginMember loginMember = new LoginMember(1L, role, true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role),
                        new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED))));
    }
}
