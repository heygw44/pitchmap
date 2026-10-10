package com.pitchmap.community.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.community.application.CommunityImageUpload;
import com.pitchmap.community.application.CommunityImageUploadService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

@WebMvcTest(CommunityImageController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class CommunityImageControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String IMAGES = "/api/community/images";
    private static final String VALID_BODY = "{\"contentType\":\"image/jpeg\",\"sizeBytes\":2483011}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CommunityImageUploadService communityImageUploadService;

    @Test
    @DisplayName("[F-29][CM-06] 이미지 업로드 URL을 발급하면 201과 imageId, uploadUrl, PUT, 서명한 헤더, 만료 시각을 응답한다")
    void issuesUploadUrl() {
        // given
        when(communityImageUploadService.issue(MEMBER_ID, "image/jpeg", 2483011L))
                .thenReturn(new CommunityImageUpload(
                        31L,
                        "https://upload.example.test/community/7/key?sig=1",
                        Map.of("Content-Type", "image/jpeg", "Content-Length", "2483011"),
                        Instant.parse("2026-11-20T10:10:00Z")));

        // when
        MvcTestResult result = send(IMAGES, verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "imageId": 31,
                  "uploadUrl": "https://upload.example.test/community/7/key?sig=1",
                  "method": "PUT",
                  "headers": { "Content-Type": "image/jpeg", "Content-Length": "2483011" },
                  "expiresAt": "2026-11-20T10:10:00Z"
                }
                """);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{\"contentType\":\"image/gif\",\"sizeBytes\":1000}",
                "{\"contentType\":\"IMAGE/JPEG\",\"sizeBytes\":1000}",
                "{\"sizeBytes\":1000}",
                "{\"contentType\":\"image/png\"}",
                "{\"contentType\":\"image/png\",\"sizeBytes\":0}",
                "{\"contentType\":\"image/png\",\"sizeBytes\":5242881}",
                "{\"contentType\":\"image/png\",\"sizeBytes\":-1}"
            })
    @DisplayName("[F-29][CM-06] 허용하지 않는 형식이거나 크기가 1~5242880 밖이거나 값이 없으면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void rejectsInvalidBody(String body) {
        // when
        MvcTestResult result = send(IMAGES, verified(), body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(communityImageUploadService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    @DisplayName("[F-29][CM-06] JPEG, PNG, WebP는 받고, 크기 상한인 5242880바이트도 받는다")
    void acceptsAllowedTypesAndMaximumSize(String contentType) {
        // given
        when(communityImageUploadService.issue(anyLong(), anyString(), anyLong()))
                .thenReturn(new CommunityImageUpload(1L, "https://u.example.test/x", Map.of(), Instant.EPOCH));

        // when
        MvcTestResult result =
                send(IMAGES, verified(), "{\"contentType\":\"%s\",\"sizeBytes\":5242880}".formatted(contentType));

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        verify(communityImageUploadService).issue(MEMBER_ID, contentType, 5242880L);
    }

    @Test
    @DisplayName("[F-29][CM-06] 로그인하지 않으면 401, 이메일 인증 전이면 403 MEMBER_NOT_VERIFIED를 응답하고 서비스를 부르지 않는다")
    void requiresVerifiedMember() {
        // when
        MvcTestResult anonymous = mvc.post()
                .uri(IMAGES)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();
        MvcTestResult unverified = send(IMAGES, unverified(), VALID_BODY);

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(communityImageUploadService);
    }

    private MvcTestResult send(String uri, RequestPostProcessor login, String body) {
        return mvc.post()
                .uri(uri)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static RequestPostProcessor verified() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority(LoginMember.AUTHORITY_EMAIL_VERIFIED))));
    }

    private static RequestPostProcessor unverified() {
        LoginMember loginMember = new LoginMember(MEMBER_ID, "USER", false);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                loginMember, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
