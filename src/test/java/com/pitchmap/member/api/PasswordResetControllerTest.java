package com.pitchmap.member.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.member.application.PasswordResetConfirmService;
import com.pitchmap.member.application.PasswordResetRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(PasswordResetController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class PasswordResetControllerTest {

    private static final String CONFIRM_PATH = "/api/auth/password-reset/confirm";
    private static final String RESET_LINK_ID = "reset-link-id-in-test";
    private static final String NEW_PASSWORD = "Valid-pass-1!";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PasswordResetRequestService passwordResetRequestService;

    @MockitoBean
    private PasswordResetConfirmService passwordResetConfirmService;

    @Test
    @DisplayName("[F-02][PW-01] 새 비밀번호 확인이 없으면 newPasswordConfirm 필드 오류로 400을 응답한다")
    void missingNewPasswordConfirmReturnsFieldError() {
        MvcTestResult result = post("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(RESET_LINK_ID, NEW_PASSWORD));

        assertInvalidInput(result);
    }

    @Test
    @DisplayName("[F-02][PW-01] 새 비밀번호 확인이 다르면 newPasswordConfirm 필드 오류 하나로 400을 응답하고 서비스를 부르지 않는다")
    void mismatchedNewPasswordConfirmReturnsSingleFieldError() {
        MvcTestResult result = post("{\"token\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirm\":\"Other-pass-2!\"}"
                .formatted(RESET_LINK_ID, NEW_PASSWORD));

        assertInvalidInput(result);
        assertThat(result).bodyJson().extractingPath("$.fieldErrors").asList().hasSize(1);
    }

    @Test
    @DisplayName("[F-02][PW-01] 새 비밀번호 확인이 같으면 204를 응답하고 토큰과 새 비밀번호를 서비스에 넘긴다")
    void matchingNewPasswordConfirmReturnsNoContent() {
        MvcTestResult result = post("{\"token\":\"%s\",\"newPassword\":\"%s\",\"newPasswordConfirm\":\"%s\"}"
                .formatted(RESET_LINK_ID, NEW_PASSWORD, NEW_PASSWORD));

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(passwordResetConfirmService).confirm(RESET_LINK_ID, NEW_PASSWORD);
    }

    private void assertInvalidInput(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='newPasswordConfirm')]")
                .asList()
                .hasSize(1);
        verifyNoInteractions(passwordResetConfirmService);
    }

    private MvcTestResult post(String body) {
        return mvc.post()
                .uri(CONFIRM_PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
