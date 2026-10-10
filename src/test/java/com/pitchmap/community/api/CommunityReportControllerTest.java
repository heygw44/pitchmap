package com.pitchmap.community.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.community.application.CommunityReportCommand;
import com.pitchmap.community.application.CommunityReportService;
import com.pitchmap.community.domain.CommunityErrorCode;
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

@WebMvcTest(CommunityReportController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class CommunityReportControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String POST_REPORTS = "/api/community/posts/9/reports";
    private static final String COMMENT_REPORTS = "/api/community/comments/5/reports";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CommunityReportService communityReportService;

    @Test
    @DisplayName("[F-29][CM-07] 인증 회원이 글을 신고하면 로그인한 회원 ID로 서비스를 부르고 본문 없이 201을 응답한다")
    void reportPostReturnsCreated() {
        // when
        MvcTestResult result = send(POST_REPORTS, verified(), "{\"reason\":\"SPAM\",\"content\":\"광고입니다\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        verify(communityReportService).reportPost(MEMBER_ID, 9L, new CommunityReportCommand("SPAM", "광고입니다"));
    }

    @Test
    @DisplayName("[F-29][CM-07] 인증 회원이 댓글을 신고하면 content 없이도 서비스를 부르고 본문 없이 201을 응답한다")
    void reportCommentReturnsCreated() {
        // when
        MvcTestResult result = send(COMMENT_REPORTS, verified(), "{\"reason\":\"ABUSE\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        verify(communityReportService).reportComment(MEMBER_ID, 5L, new CommunityReportCommand("ABUSE", null));
    }

    @Test
    @DisplayName("[F-29][CM-07] reason이 없거나 content가 1001자이면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void invalidBodyIsBadRequest() {
        // when
        MvcTestResult noReason = send(POST_REPORTS, verified(), "{\"content\":\"내용\"}");
        MvcTestResult longContent =
                send(COMMENT_REPORTS, verified(), "{\"reason\":\"SPAM\",\"content\":\"" + "가".repeat(1001) + "\"}");

        // then
        assertThat(noReason).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(noReason).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(longContent).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(longContent).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(communityReportService);
    }

    @Test
    @DisplayName("[F-29][CM-07] 로그인하지 않으면 401 AUTHENTICATION_REQUIRED, 이메일 인증 전이면 403 MEMBER_NOT_VERIFIED를 응답한다")
    void requiresVerifiedMember() {
        // when
        MvcTestResult anonymous = mvc.post()
                .uri(POST_REPORTS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"SPAM\"}")
                .exchange();
        MvcTestResult unverified = send(COMMENT_REPORTS, unverified(), "{\"reason\":\"SPAM\"}");

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(unverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(communityReportService);
    }

    @Test
    @DisplayName("[F-29][CM-07] 서비스가 던진 COMMUNITY_ALREADY_REPORTED는 409로, NOT_FOUND는 404로 응답한다")
    void mapsServiceErrors() {
        // given
        doThrow(new BusinessException(CommunityErrorCode.COMMUNITY_ALREADY_REPORTED))
                .when(communityReportService)
                .reportPost(anyLong(), anyLong(), any());
        doThrow(new BusinessException(CommonErrorCode.NOT_FOUND))
                .when(communityReportService)
                .reportComment(anyLong(), anyLong(), any());

        // when
        MvcTestResult conflict = send(POST_REPORTS, verified(), "{\"reason\":\"SPAM\"}");
        MvcTestResult notFound = send(COMMENT_REPORTS, verified(), "{\"reason\":\"SPAM\"}");

        // then
        assertThat(conflict).hasStatus(HttpStatus.CONFLICT);
        assertThat(conflict).bodyJson().extractingPath("$.code").isEqualTo("COMMUNITY_ALREADY_REPORTED");
        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-29][CM-07] 모르는 reason도 서비스까지 그대로 넘기고, 서비스가 던진 INVALID_INPUT은 400으로 응답한다")
    void unknownReasonIsRejectedByService() {
        // given
        doThrow(new BusinessException(CommonErrorCode.INVALID_INPUT, "신고 사유가 올바르지 않습니다."))
                .when(communityReportService)
                .reportPost(anyLong(), anyLong(), any());

        // when
        MvcTestResult result = send(POST_REPORTS, verified(), "{\"reason\":\"UNKNOWN\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verify(communityReportService).reportPost(MEMBER_ID, 9L, new CommunityReportCommand("UNKNOWN", null));
    }

    private MvcTestResult send(String uri, RequestPostProcessor user, String body) {
        return mvc.post()
                .uri(uri)
                .with(user)
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
