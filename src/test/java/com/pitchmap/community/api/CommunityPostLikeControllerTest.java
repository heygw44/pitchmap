package com.pitchmap.community.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.pitchmap.community.application.CommunityPostLikeResult;
import com.pitchmap.community.application.CommunityPostLikeService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CommunityPostLikeController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class CommunityPostLikeControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String LIKE = "/api/community/posts/9/like";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CommunityPostLikeService communityPostLikeService;

    @Test
    @DisplayName("[F-29][CM-05] 인증 회원이 PUT으로 좋아요를 누르면 로그인한 회원 ID로 서비스를 부르고 liked와 likeCount를 응답한다")
    void putLikeReturnsLikedAndCount() {
        // given
        when(communityPostLikeService.like(MEMBER_ID, 9L)).thenReturn(new CommunityPostLikeResult(true, 4L));

        // when
        MvcTestResult result = mvc.put().uri(LIKE).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                { "liked": true, "likeCount": 4 }
                """);
        verify(communityPostLikeService).like(MEMBER_ID, 9L);
    }

    @Test
    @DisplayName("[F-29][CM-05] 인증 회원이 DELETE로 좋아요를 취소하면 로그인한 회원 ID로 서비스를 부르고 liked와 likeCount를 응답한다")
    void deleteLikeReturnsUnlikedAndCount() {
        // given
        when(communityPostLikeService.unlike(MEMBER_ID, 9L)).thenReturn(new CommunityPostLikeResult(false, 3L));

        // when
        MvcTestResult result =
                mvc.delete().uri(LIKE).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                { "liked": false, "likeCount": 3 }
                """);
        verify(communityPostLikeService).unlike(MEMBER_ID, 9L);
    }

    @Test
    @DisplayName("[F-29][CM-05] 로그인하지 않은 사용자가 좋아요를 누르거나 취소하면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousCannotLike() {
        // when
        MvcTestResult put = mvc.put().uri(LIKE).with(csrf()).exchange();
        MvcTestResult delete = mvc.delete().uri(LIKE).with(csrf()).exchange();

        // then
        assertThat(put).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(put).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(delete).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(delete).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(communityPostLikeService);
    }

    @Test
    @DisplayName("[F-29][CM-05] 이메일 인증을 마치지 않은 회원이 좋아요를 누르거나 취소하면 403 MEMBER_NOT_VERIFIED를 응답한다")
    void unverifiedMemberCannotLike() {
        // when
        MvcTestResult put = mvc.put().uri(LIKE).with(unverified()).with(csrf()).exchange();
        MvcTestResult delete =
                mvc.delete().uri(LIKE).with(unverified()).with(csrf()).exchange();

        // then
        assertThat(put).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(put).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(communityPostLikeService);
    }

    @Test
    @DisplayName("[F-29][CM-03] 서비스가 던진 NOT_FOUND를 좋아요 누르기와 취소에서 404로 응답한다")
    void missingPostIsNotFound() {
        // given
        when(communityPostLikeService.like(anyLong(), anyLong()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(communityPostLikeService.unlike(anyLong(), anyLong()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult put = mvc.put().uri(LIKE).with(verified()).with(csrf()).exchange();
        MvcTestResult delete =
                mvc.delete().uri(LIKE).with(verified()).with(csrf()).exchange();

        // then
        assertThat(put).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(put).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(delete).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(delete).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
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
