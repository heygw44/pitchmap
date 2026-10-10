package com.pitchmap.admin.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.community.application.AdminCommunityAuthor;
import com.pitchmap.community.application.AdminCommunityCommentSummary;
import com.pitchmap.community.application.AdminCommunityPage;
import com.pitchmap.community.application.AdminCommunityPostSummary;
import com.pitchmap.community.application.AdminCommunityQueryService;
import com.pitchmap.community.application.AdminCommunityReasonCounts;
import com.pitchmap.community.application.AdminCommunityRecentReport;
import com.pitchmap.community.application.CommunityModerationResult;
import com.pitchmap.community.application.CommunityModerationService;
import com.pitchmap.community.domain.CommunityErrorCode;
import java.time.Instant;
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

@WebMvcTest(AdminCommunityController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class AdminCommunityControllerTest {

    private static final String BASE = "/api/admin/community";
    private static final Instant AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final long ADMIN_ID = 1L;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AdminCommunityQueryService queryService;

    @MockitoBean
    private CommunityModerationService moderationService;

    @Test
    @DisplayName("[F-29][CM-08] 로그인하지 않으면 401, 관리자가 아닌 회원이 커뮤니티 검토 API를 부르면 403 ACCESS_DENIED이다")
    void requireAdmin() {
        MvcTestResult anonymous = mvc.get().uri(BASE + "/posts").exchange();
        MvcTestResult userPosts =
                mvc.get().uri(BASE + "/posts").with(member("USER")).exchange();
        MvcTestResult userComments =
                mvc.get().uri(BASE + "/comments").with(member("USER")).exchange();
        MvcTestResult userHide = post(member("USER"), BASE + "/posts/9/hide");
        MvcTestResult userRestore = post(member("USER"), BASE + "/comments/9/restore");

        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        for (MvcTestResult result : List.of(userPosts, userComments, userHide, userRestore)) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        }
        verifyNoInteractions(queryService, moderationService);
    }

    @Test
    @DisplayName("[F-29][CM-08] 글 검토 목록은 작성자, 사유별 신고 수, 최근 신고를 담아 응답하고 status를 생략하면 서비스에 null을 넘긴다")
    void postListReturnsSummaries() {
        // given
        var summary = new AdminCommunityPostSummary(
                9L,
                "제목",
                "본문 전체",
                new AdminCommunityAuthor(4L, "작성자"),
                "PENDING_REVIEW",
                5L,
                new AdminCommunityReasonCounts(2L, 1L, 0L, 0L, 1L, 1L),
                List.of(new AdminCommunityRecentReport("MONEY_SCAM", "돈을 요구한다", AT)),
                AT);
        when(queryService.listPosts(eq(null), anyInt(), anyInt()))
                .thenReturn(new AdminCommunityPage<>(List.of(summary), 0, 20, true));

        // when
        MvcTestResult result = mvc.get().uri(BASE + "/posts").with(admin()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "postId": 9, "title": "제목", "content": "본문 전체",
                    "author": {"memberId": 4, "nickname": "작성자"},
                    "status": "PENDING_REVIEW", "reportCount": 5,
                    "reasonCounts": {"SPAM": 2, "ABUSE": 1, "ILLEGAL_CAMPING": 0, "PRIVACY": 0, "MONEY_SCAM": 1, "OTHER": 1},
                    "recentReports": [{"reason": "MONEY_SCAM", "content": "돈을 요구한다", "createdAt": "2026-10-05T03:00:00Z"}],
                    "statusChangedAt": "2026-10-05T03:00:00Z"
                  }],
                  "page": 0, "size": 20, "hasNext": true
                }
                """);
    }

    @Test
    @DisplayName("[F-29][CM-08] 댓글 검토 목록은 글 ID와 작성자를 담아 응답하고 지운 댓글의 content는 null이다")
    void commentListReturnsSummaries() {
        // given
        var summary = new AdminCommunityCommentSummary(
                12L,
                9L,
                null,
                new AdminCommunityAuthor(4L, "작성자"),
                "HIDDEN",
                0L,
                new AdminCommunityReasonCounts(0L, 0L, 0L, 0L, 0L, 0L),
                List.of(),
                AT);
        when(queryService.listComments(eq("HIDDEN"), anyInt(), anyInt()))
                .thenReturn(new AdminCommunityPage<>(List.of(summary), 0, 20, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(BASE + "/comments")
                .param("status", "HIDDEN")
                .with(admin())
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].commentId").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$.content[0].postId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.content[0].content").isNull();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo("작성자");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-29][CM-08] 목록의 size가 1~50을 벗어나거나 page가 음수이면 400 INVALID_INPUT이고, 알 수 없는 status는 서비스가 400으로 거부한다")
    void listRejectsInvalidParameters() {
        for (String path : List.of(BASE + "/posts", BASE + "/comments")) {
            for (String[] param : new String[][] {{"size", "51"}, {"size", "0"}, {"page", "-1"}}) {
                MvcTestResult result = mvc.get()
                        .uri(path)
                        .param(param[0], param[1])
                        .with(admin())
                        .exchange();

                assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
                assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
            }
        }
        verifyNoInteractions(queryService);
        when(queryService.listPosts(eq("ACTIVE"), anyInt(), anyInt()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT, "status가 올바르지 않습니다."));

        MvcTestResult unknown = mvc.get()
                .uri(BASE + "/posts")
                .param("status", "ACTIVE")
                .with(admin())
                .exchange();

        assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknown).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-29][CM-08] 글과 댓글 숨김·복구는 로그인한 관리자 ID로 서비스를 부르고 대상 ID와 상태를 응답한다")
    void hideAndRestoreReturnStatus() {
        // given
        when(moderationService.hidePost(9L, ADMIN_ID)).thenReturn(new CommunityModerationResult(9L, "HIDDEN"));
        when(moderationService.restorePost(9L, ADMIN_ID)).thenReturn(new CommunityModerationResult(9L, "ACTIVE"));
        when(moderationService.hideComment(5L, ADMIN_ID)).thenReturn(new CommunityModerationResult(5L, "HIDDEN"));
        when(moderationService.restoreComment(5L, ADMIN_ID)).thenReturn(new CommunityModerationResult(5L, "ACTIVE"));

        // when
        MvcTestResult hidePost = post(admin(), BASE + "/posts/9/hide");
        MvcTestResult restorePost = post(admin(), BASE + "/posts/9/restore");
        MvcTestResult hideComment = post(admin(), BASE + "/comments/5/hide");
        MvcTestResult restoreComment = post(admin(), BASE + "/comments/5/restore");

        // then
        assertThat(hidePost).hasStatus(HttpStatus.OK);
        assertThat(hidePost).bodyJson().isStrictlyEqualTo("{\"postId\":9,\"status\":\"HIDDEN\"}");
        assertThat(restorePost).bodyJson().isStrictlyEqualTo("{\"postId\":9,\"status\":\"ACTIVE\"}");
        assertThat(hideComment).bodyJson().isStrictlyEqualTo("{\"commentId\":5,\"status\":\"HIDDEN\"}");
        assertThat(restoreComment).bodyJson().isStrictlyEqualTo("{\"commentId\":5,\"status\":\"ACTIVE\"}");
    }

    @Test
    @DisplayName("[F-29][CM-08] 서비스가 던진 NOT_FOUND는 404로, COMMUNITY_INVALID_STATE는 409로 응답한다")
    void hideAndRestoreMapErrors() {
        // given
        when(moderationService.hidePost(eq(1L), anyLong())).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        when(moderationService.restoreComment(eq(2L), anyLong()))
                .thenThrow(new BusinessException(CommunityErrorCode.COMMUNITY_INVALID_STATE));

        // when
        MvcTestResult notFound = post(admin(), BASE + "/posts/1/hide");
        MvcTestResult conflict = post(admin(), BASE + "/comments/2/restore");

        // then
        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(conflict).hasStatus(HttpStatus.CONFLICT);
        assertThat(conflict).bodyJson().extractingPath("$.code").isEqualTo("COMMUNITY_INVALID_STATE");
    }

    private MvcTestResult post(RequestPostProcessor user, String uri) {
        return mvc.post().uri(uri).with(user).with(csrf()).exchange();
    }

    private static RequestPostProcessor admin() {
        return member("ADMIN");
    }

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
