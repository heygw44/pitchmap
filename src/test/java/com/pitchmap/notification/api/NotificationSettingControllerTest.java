package com.pitchmap.notification.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.notification.application.NotificationSettingItem;
import com.pitchmap.notification.application.NotificationSettingService;
import java.util.List;
import java.util.stream.IntStream;
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

@WebMvcTest(NotificationSettingController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class NotificationSettingControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String PATH = "/api/me/notification-settings";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private NotificationSettingService notificationSettingService;

    @Test
    @DisplayName("[F-20] 로그인하지 않으면 조회와 변경 모두 401 AUTHENTICATION_REQUIRED이고 서비스를 부르지 않는다")
    void anonymousIsUnauthorized() {
        MvcTestResult get = mvc.get().uri(PATH).exchange();
        MvcTestResult put = put("[]", false);

        assertThat(get).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(put).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(put).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(notificationSettingService);
    }

    @Test
    @DisplayName("[F-20] 조회는 type과 emailEnabled를 가진 항목의 배열이다")
    void getReturnsArray() {
        when(notificationSettingService.list(MEMBER_ID))
                .thenReturn(List.of(
                        new NotificationSettingItem("BASECAMP_APPLIED", true),
                        new NotificationSettingItem("BASECAMP_APPROVED", false)));

        MvcTestResult result = mvc.get().uri(PATH).with(login()).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                [
                  { "type": "BASECAMP_APPLIED", "emailEnabled": true },
                  { "type": "BASECAMP_APPROVED", "emailEnabled": false }
                ]
                """);
    }

    @Test
    @DisplayName("[F-20] 변경하면 요청 항목을 서비스에 넘기고 서비스가 돌려준 결과를 200으로 응답한다")
    void putReturnsResult() {
        List<NotificationSettingItem> requested = List.of(new NotificationSettingItem("BASECAMP_APPLIED", false));
        when(notificationSettingService.replace(MEMBER_ID, requested)).thenReturn(requested);

        MvcTestResult result = put("[{\"type\":\"BASECAMP_APPLIED\",\"emailEnabled\":false}]", true);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                [ { "type": "BASECAMP_APPLIED", "emailEnabled": false } ]
                """);
        verify(notificationSettingService).replace(MEMBER_ID, requested);
    }

    @Test
    @DisplayName("[F-20] type이 비었거나 emailEnabled가 없거나 항목이 null이면 400 INVALID_INPUT이다")
    void putRejectsInvalidItems() {
        MvcTestResult blank = put("[{\"type\":\" \",\"emailEnabled\":true}]", true);
        MvcTestResult missingFlag = put("[{\"type\":\"BASECAMP_APPLIED\"}]", true);
        MvcTestResult nullItem = put("[null]", true);

        for (MvcTestResult result : List.of(blank, missingFlag, nullItem)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(blank).bodyJson().extractingPath("$.fieldErrors").isNotNull();
        verifyNoInteractions(notificationSettingService);
    }

    @Test
    @DisplayName("[F-20] 항목이 알림 종류 수보다 많으면 400 INVALID_INPUT이다")
    void putRejectsTooManyItems() {
        String items = IntStream.rangeClosed(0, NotificationSettingService.TYPE_COUNT)
                .mapToObj(i -> "{\"type\":\"T" + i + "\",\"emailEnabled\":true}")
                .reduce((a, b) -> a + "," + b)
                .orElseThrow();

        MvcTestResult result = put("[" + items + "]", true);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(notificationSettingService);
    }

    @Test
    @DisplayName("[F-20] 서비스가 알 수 없거나 중복된 종류로 INVALID_INPUT을 던지면 400으로 응답한다")
    void putMapsServiceInvalidInput() {
        when(notificationSettingService.replace(anyLong(), anyList()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT, "알 수 없는 알림 종류입니다."));

        MvcTestResult result = put("[{\"type\":\"NO_SUCH\",\"emailEnabled\":true}]", true);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-20] CSRF 토큰 없이 변경을 요청하면 403이고 서비스를 부르지 않는다")
    void putWithoutCsrfIsForbidden() {
        MvcTestResult result = mvc.put()
                .uri(PATH)
                .with(login())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(notificationSettingService);
    }

    private MvcTestResult put(String json, boolean loggedIn) {
        var request = mvc.put()
                .uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .with(csrf());
        if (loggedIn) {
            request = request.with(login());
        }
        return request.exchange();
    }

    private static RequestPostProcessor login() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
