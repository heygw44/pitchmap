package com.pitchmap.community.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import com.pitchmap.community.application.CommunityCommentCommandService;
import com.pitchmap.community.application.CommunityCommentItem;
import com.pitchmap.community.application.CommunityCommentPage;
import com.pitchmap.community.application.CommunityCommentQueryService;
import com.pitchmap.community.application.CommunityCommentWriteCommand;
import java.time.Instant;
import java.util.List;
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

@WebMvcTest(CommunityCommentController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class CommunityCommentControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String COMMENTS = "/api/community/posts/9/comments";
    private static final String COMMENT = "/api/community/comments/21";
    private static final Instant CREATED_AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-05T04:00:00Z");
    private static final CommunityCommentItem REPLY =
            new CommunityCommentItem(22L, false, 32L, "아침안개", "저도요", CREATED_AT, CREATED_AT, List.of());
    private static final CommunityCommentItem WITH_REPLY =
            new CommunityCommentItem(21L, false, 31L, "새벽능선", "좋아요", CREATED_AT, UPDATED_AT, List.of(REPLY));
    private static final CommunityCommentItem PLACEHOLDER =
            new CommunityCommentItem(20L, true, null, null, null, null, null, List.of(REPLY));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CommunityCommentCommandService communityCommentCommandService;

    @MockitoBean
    private CommunityCommentQueryService communityCommentQueryService;

    @Test
    @DisplayName("[F-29][CM-04] 로그인하지 않은 사용자도 댓글 목록을 조회하면 댓글 아래에 답글이 묶여 나온다")
    void anonymousReadsCommentList() {
        // given
        when(communityCommentQueryService.list(9L, 0, 20))
                .thenReturn(new CommunityCommentPage(List.of(WITH_REPLY), 0, 20, true));

        // when
        MvcTestResult result = mvc.get().uri(COMMENTS).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "commentId": 21,
                    "author": { "memberId": 31, "nickname": "새벽능선" },
                    "content": "좋아요",
                    "deleted": false,
                    "createdAt": "2026-10-05T03:00:00Z",
                    "updatedAt": "2026-10-05T04:00:00Z",
                    "replies": [{
                      "commentId": 22,
                      "author": { "memberId": 32, "nickname": "아침안개" },
                      "content": "저도요",
                      "deleted": false,
                      "createdAt": "2026-10-05T03:00:00Z",
                      "updatedAt": "2026-10-05T03:00:00Z",
                      "replies": []
                    }]
                  }],
                  "page": 0, "size": 20, "hasNext": true
                }
                """);
    }

    @Test
    @DisplayName("[F-29][CM-04] 삭제된 댓글 자리는 commentId, deleted, replies만 있고 작성자·내용·시각 필드가 JSON에 없다")
    void deletedPlaceholderOmitsAuthorContentAndTimes() {
        // given
        when(communityCommentQueryService.list(9L, 0, 20))
                .thenReturn(new CommunityCommentPage(List.of(PLACEHOLDER), 0, 20, false));

        // when
        MvcTestResult result = mvc.get().uri(COMMENTS).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "commentId": 20,
                    "deleted": true,
                    "replies": [{
                      "commentId": 22,
                      "author": { "memberId": 32, "nickname": "아침안개" },
                      "content": "저도요",
                      "deleted": false,
                      "createdAt": "2026-10-05T03:00:00Z",
                      "updatedAt": "2026-10-05T03:00:00Z",
                      "replies": []
                    }]
                  }],
                  "page": 0, "size": 20, "hasNext": false
                }
                """);
    }

    @Test
    @DisplayName("[F-29] 목록의 page와 size를 서비스에 넘기고, 보내지 않으면 page 0, size 20을 쓴다")
    void listPassesPagingAndDefaults() {
        // given
        when(communityCommentQueryService.list(any(Long.class), any(Integer.class), any(Integer.class)))
                .thenReturn(new CommunityCommentPage(List.of(), 0, 20, false));

        // when
        MvcTestResult paged = mvc.get().uri(COMMENTS + "?page=2&size=50").exchange();
        MvcTestResult defaults = mvc.get().uri(COMMENTS).exchange();

        // then
        assertThat(paged).hasStatus(HttpStatus.OK);
        assertThat(defaults).hasStatus(HttpStatus.OK);
        verify(communityCommentQueryService).list(9L, 2, 50);
        verify(communityCommentQueryService).list(9L, 0, 20);
    }

    @Test
    @DisplayName("[F-29] 범위를 벗어난 size나 page는 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void listRejectsOutOfRangePaging() {
        // when
        MvcTestResult tooBig = mvc.get().uri(COMMENTS + "?size=51").exchange();
        MvcTestResult zero = mvc.get().uri(COMMENTS + "?size=0").exchange();
        MvcTestResult negativePage = mvc.get().uri(COMMENTS + "?page=-1").exchange();

        // then
        assertThat(tooBig).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooBig).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(zero).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(negativePage).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(communityCommentQueryService);
    }

    @Test
    @DisplayName("[F-29][CM-04] 댓글을 쓰면 201과 commentId를 응답하고, 내용·부모 댓글·글 ID·로그인한 회원의 ID를 서비스에 넘긴다")
    void writeReturnsCreatedWithCommentId() {
        // given
        when(communityCommentCommandService.write(eq(MEMBER_ID), eq(9L), any())).thenReturn(55L);

        // when
        MvcTestResult result = send(mvc.post().uri(COMMENTS), verified(), "{\"content\":\"답글이에요\",\"parentId\":21}");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"commentId\": 55 }");
        verify(communityCommentCommandService).write(MEMBER_ID, 9L, new CommunityCommentWriteCommand("답글이에요", 21L));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"content\":\"   \"}", "{\"content\":null}"})
    @DisplayName("[F-29] 내용이 없거나 공백뿐이면 400 INVALID_INPUT과 content 필드 오류를 응답하고 서비스를 부르지 않는다")
    void writeRejectsBlankContent(String body) {
        // when
        MvcTestResult result = send(mvc.post().uri(COMMENTS), verified(), body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='content')]")
                .asList()
                .hasSize(1);
        verifyNoInteractions(communityCommentCommandService);
    }

    @Test
    @DisplayName("[F-29] 내용 1000자는 받고 1001자는 필드 오류로 400을 응답한다. 수정도 같다")
    void contentLengthBoundaries() {
        // given
        when(communityCommentCommandService.write(any(Long.class), any(Long.class), any()))
                .thenReturn(1L);
        when(communityCommentCommandService.revise(any(Long.class), any(Long.class), any()))
                .thenReturn(WITH_REPLY);

        // when
        MvcTestResult createdAtLimit =
                send(mvc.post().uri(COMMENTS), verified(), "{\"content\":\"" + "가".repeat(1000) + "\"}");
        MvcTestResult createdTooLong =
                send(mvc.post().uri(COMMENTS), verified(), "{\"content\":\"" + "가".repeat(1001) + "\"}");
        MvcTestResult updatedAtLimit =
                send(mvc.patch().uri(COMMENT), verified(), "{\"content\":\"" + "가".repeat(1000) + "\"}");
        MvcTestResult updatedTooLong =
                send(mvc.patch().uri(COMMENT), verified(), "{\"content\":\"" + "가".repeat(1001) + "\"}");

        // then
        assertThat(createdAtLimit).hasStatus(HttpStatus.CREATED);
        assertThat(createdTooLong).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(createdTooLong)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='content')]")
                .asList()
                .hasSize(1);
        assertThat(updatedAtLimit).hasStatus(HttpStatus.OK);
        assertThat(updatedTooLong).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("[F-29] 로그인하지 않은 사용자가 쓰거나 고치거나 지우면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousWriteIsUnauthorized() {
        // when
        MvcTestResult write = mvc.post()
                .uri(COMMENTS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"댓글\"}")
                .exchange();
        MvcTestResult update = mvc.patch()
                .uri(COMMENT)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"댓글\"}")
                .exchange();
        MvcTestResult delete = mvc.delete().uri(COMMENT).with(csrf()).exchange();

        // then
        assertThat(write).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(write).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(update).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(delete).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(communityCommentCommandService);
    }

    @Test
    @DisplayName("[F-29][TR-03] 이메일 인증 전의 회원이 쓰거나 고치거나 지우면 403 MEMBER_NOT_VERIFIED를 응답한다")
    void unverifiedMemberCannotWrite() {
        // when
        MvcTestResult write = send(mvc.post().uri(COMMENTS), unverified(), "{\"content\":\"댓글\"}");
        MvcTestResult update = send(mvc.patch().uri(COMMENT), unverified(), "{\"content\":\"댓글\"}");
        MvcTestResult delete =
                mvc.delete().uri(COMMENT).with(unverified()).with(csrf()).exchange();

        // then
        assertThat(write).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(write).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(update).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(update).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(communityCommentCommandService);
    }

    @Test
    @DisplayName("[F-29] 댓글을 고치면 200과 고친 댓글 항목을 응답하고, 로그인한 회원의 ID와 내용을 서비스에 넘긴다")
    void updateReturnsRevisedItem() {
        // given
        when(communityCommentCommandService.revise(eq(MEMBER_ID), eq(21L), any()))
                .thenReturn(WITH_REPLY);

        // when
        MvcTestResult result = send(mvc.patch().uri(COMMENT), verified(), "{\"content\":\"새 내용\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.commentId").isEqualTo(21);
        assertThat(result).bodyJson().extractingPath("$.replies[0].commentId").isEqualTo(22);
        verify(communityCommentCommandService).revise(MEMBER_ID, 21L, "새 내용");
    }

    @Test
    @DisplayName("[F-29][CM-03] 댓글을 지우면 204를 본문 없이 응답하고, 로그인한 회원의 ID로 서비스를 부른다")
    void deleteReturnsNoContent() {
        // when
        MvcTestResult result =
                mvc.delete().uri(COMMENT).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(result).body().isEmpty();
        verify(communityCommentCommandService).delete(MEMBER_ID, 21L);
    }

    private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder builder, RequestPostProcessor login, String body) {
        return builder.with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    // 로그인 서비스가 세션에 넣는 것과 같은 모양의 인증 정보를 만든다. 이메일 인증 권한을 붙여 인증 회원 전용 경로를 연다.
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
