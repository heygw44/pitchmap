package com.pitchmap.community.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.pitchmap.common.web.PatchField;
import com.pitchmap.community.application.CommunityPostCommandService;
import com.pitchmap.community.application.CommunityPostItem;
import com.pitchmap.community.application.CommunityPostPage;
import com.pitchmap.community.application.CommunityPostQueryService;
import com.pitchmap.community.application.CommunityPostReviseCommand;
import com.pitchmap.community.application.CommunityPostWriteCommand;
import com.pitchmap.community.domain.CommunityCategory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(CommunityPostController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class CommunityPostControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String POSTS = "/api/community/posts";
    private static final String VALID_BODY =
            "{\"category\":\"GEAR\",\"title\":\"텐트 후기\",\"content\":\"가볍다\",\"spotId\":101}";
    private static final Instant CREATED_AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-05T04:00:00Z");
    private static final CommunityPostItem ITEM = new CommunityPostItem(
            9L,
            CommunityCategory.GEAR,
            "텐트 후기",
            "가볍다",
            31L,
            "새벽능선",
            101L,
            "능선 끝 평지",
            5L,
            3L,
            CREATED_AT,
            UPDATED_AT,
            null);
    private static final CommunityPostItem ITEM_WITHOUT_SPOT = new CommunityPostItem(
            10L, CommunityCategory.FREE, "안녕", "반가워요", 31L, "새벽능선", null, null, 0L, 0L, CREATED_AT, CREATED_AT, null);

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CommunityPostCommandService communityPostCommandService;

    @MockitoBean
    private CommunityPostQueryService communityPostQueryService;

    @Test
    @DisplayName("[F-29] 로그인하지 않은 사용자도 글 목록을 조회하면 항목의 모든 필드와 페이지 정보를 받고, 좋아요 수와 댓글 수는 있고 이미지 필드와 likedByMe는 없다")
    void anonymousReadsPostList() {
        // given
        when(communityPostQueryService.list(null, null, 0, 20))
                .thenReturn(new CommunityPostPage(List.of(ITEM), 0, 20, true));

        // when
        MvcTestResult result = mvc.get().uri(POSTS).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "postId": 9,
                    "category": "GEAR",
                    "title": "텐트 후기",
                    "excerpt": "가볍다",
                    "author": { "memberId": 31, "nickname": "새벽능선" },
                    "spot": { "spotId": 101, "name": "능선 끝 평지" },
                    "likeCount": 5,
                  "commentCount": 3,
                    "createdAt": "2026-10-05T03:00:00Z"
                  }],
                  "page": 0, "size": 20, "hasNext": true
                }
                """);
    }

    @Test
    @DisplayName("[F-29][CM-02] 연결한 장소가 없거나 보이지 않는 글은 spot 필드가 null이 아니라 아예 없다")
    void listOmitsSpotFieldWhenNoVisibleSpot() {
        // given
        when(communityPostQueryService.list(null, null, 0, 20))
                .thenReturn(new CommunityPostPage(List.of(ITEM_WITHOUT_SPOT), 0, 20, false));

        // when
        MvcTestResult result = mvc.get().uri(POSTS).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "postId": 10,
                    "category": "FREE",
                    "title": "안녕",
                    "excerpt": "반가워요",
                    "author": { "memberId": 31, "nickname": "새벽능선" },
                    "likeCount": 0,
                  "commentCount": 0,
                    "createdAt": "2026-10-05T03:00:00Z"
                  }],
                  "page": 0, "size": 20, "hasNext": false
                }
                """);
    }

    @Test
    @DisplayName("[F-29] 목록의 category, spotId, page, size를 서비스에 그대로 넘긴다")
    void listPassesFiltersAndPaging() {
        // given
        when(communityPostQueryService.list(CommunityCategory.GEAR, 101L, 2, 50))
                .thenReturn(new CommunityPostPage(List.of(), 2, 50, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(POSTS + "?category=GEAR&spotId=101&page=2&size=50")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(communityPostQueryService).list(CommunityCategory.GEAR, 101L, 2, 50);
    }

    @Test
    @DisplayName("[F-29] 알 수 없는 category나 범위를 벗어난 size·page는 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void listRejectsUnknownCategoryAndOutOfRangePaging() {
        // when
        MvcTestResult unknownCategory =
                mvc.get().uri(POSTS + "?category=CAMPING").exchange();
        MvcTestResult tooBig = mvc.get().uri(POSTS + "?size=51").exchange();
        MvcTestResult negativePage = mvc.get().uri(POSTS + "?page=-1").exchange();

        // then
        assertThat(unknownCategory).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknownCategory).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(tooBig).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooBig).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(negativePage).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(communityPostQueryService);
    }

    @Test
    @DisplayName("[F-29] 로그인하지 않은 사용자도 글 상세를 조회하면 본문과 수정 시각을 받고 likedByMe 필드는 없다")
    void anonymousReadsDetail() {
        // given
        when(communityPostQueryService.detail(9L, null)).thenReturn(ITEM);

        // when
        MvcTestResult result = mvc.get().uri(POSTS + "/9").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "postId": 9,
                  "category": "GEAR",
                  "title": "텐트 후기",
                  "content": "가볍다",
                  "author": { "memberId": 31, "nickname": "새벽능선" },
                  "spot": { "spotId": 101, "name": "능선 끝 평지" },
                  "likeCount": 5,
                  "commentCount": 3,
                  "createdAt": "2026-10-05T03:00:00Z",
                  "updatedAt": "2026-10-05T04:00:00Z"
                }
                """);
    }

    @Test
    @DisplayName("[F-29][CM-05] 인증 회원이 글 상세를 조회하면 자기 회원 ID로 조회하고 likedByMe를 받는다")
    void verifiedMemberReadsDetailWithLikedByMe() {
        // given
        CommunityPostItem liked = new CommunityPostItem(
                9L,
                CommunityCategory.GEAR,
                "텐트 후기",
                "가볍다",
                31L,
                "새벽능선",
                null,
                null,
                5L,
                3L,
                CREATED_AT,
                UPDATED_AT,
                true);
        when(communityPostQueryService.detail(9L, MEMBER_ID)).thenReturn(liked);

        // when
        MvcTestResult result = mvc.get().uri(POSTS + "/9").with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.likedByMe").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.likeCount").isEqualTo(5);
        verify(communityPostQueryService).detail(9L, MEMBER_ID);
    }

    @Test
    @DisplayName("[F-29][CM-03] 서비스가 던진 NOT_FOUND를 글 상세에서 404로 응답한다")
    void detailOfMissingPostIsNotFound() {
        // given
        when(communityPostQueryService.detail(anyLong(), any()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult result = mvc.get().uri(POSTS + "/9").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-29] 글을 쓰면 201과 postId를 응답하고, 요청 값과 로그인한 회원의 ID를 서비스에 넘긴다")
    void writeReturnsCreatedWithPostId() {
        // given
        when(communityPostCommandService.write(eq(MEMBER_ID), any())).thenReturn(55L);

        // when
        MvcTestResult result = send(mvc.post().uri(POSTS), verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"postId\": 55 }");
        ArgumentCaptor<CommunityPostWriteCommand> captor = ArgumentCaptor.forClass(CommunityPostWriteCommand.class);
        verify(communityPostCommandService).write(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new CommunityPostWriteCommand(CommunityCategory.GEAR, "텐트 후기", "가볍다", 101L));
    }

    @Test
    @DisplayName("[F-29] 장소를 연결하지 않은 글도 쓸 수 있고, 서비스에는 spotId가 null로 간다")
    void writeWithoutSpot() {
        // given
        when(communityPostCommandService.write(eq(MEMBER_ID), any())).thenReturn(56L);

        // when
        MvcTestResult result = send(
                mvc.post().uri(POSTS), verified(), "{\"category\":\"FREE\",\"title\":\"안녕\",\"content\":\"반가워요\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        verify(communityPostCommandService)
                .write(MEMBER_ID, new CommunityPostWriteCommand(CommunityCategory.FREE, "안녕", "반가워요", null));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{\"title\":\"제목\",\"content\":\"본문\"}",
                "{\"category\":\"FREE\",\"content\":\"본문\"}",
                "{\"category\":\"FREE\",\"title\":\"  \",\"content\":\"본문\"}",
                "{\"category\":\"FREE\",\"title\":\"제목\"}",
                "{\"category\":\"FREE\",\"title\":\"제목\",\"content\":\"   \"}",
                "{\"category\":\"CAMPING\",\"title\":\"제목\",\"content\":\"본문\"}"
            })
    @DisplayName("[F-29][CM-02] 카테고리가 없거나 틀리고, 제목이나 본문이 비면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void writeRejectsInvalidBody(String body) {
        // when
        MvcTestResult result = send(mvc.post().uri(POSTS), verified(), body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(communityPostCommandService);
    }

    @Test
    @DisplayName("[F-29][CM-02] 제목 100자와 본문 10000자는 받고, 101자와 10001자는 필드 오류로 400을 응답한다")
    void writeChecksLengthBoundaries() {
        // given
        when(communityPostCommandService.write(anyLong(), any())).thenReturn(1L);

        // when
        MvcTestResult atLimit = send(mvc.post().uri(POSTS), verified(), body("가".repeat(100), "나".repeat(10000)));
        MvcTestResult longTitle = send(mvc.post().uri(POSTS), verified(), body("가".repeat(101), "본문"));
        MvcTestResult longContent = send(mvc.post().uri(POSTS), verified(), body("제목", "나".repeat(10001)));

        // then
        assertThat(atLimit).hasStatus(HttpStatus.CREATED);
        assertThat(longTitle).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(longTitle)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='title')]")
                .asList()
                .hasSize(1);
        assertThat(longContent).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(longContent)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='content')]")
                .asList()
                .hasSize(1);
    }

    @Test
    @DisplayName("[F-29][CM-02] 연결할 장소가 ACTIVE가 아니어서 서비스가 던진 INVALID_INPUT을 400으로 응답한다")
    void writeWithUnlinkableSpotIsBadRequest() {
        // given
        when(communityPostCommandService.write(anyLong(), any()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT, "연결할 수 없는 장소입니다."));

        // when
        MvcTestResult result = send(mvc.post().uri(POSTS), verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-29] 로그인하지 않은 사용자가 쓰면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousWriteIsUnauthorized() {
        // when
        MvcTestResult result = mvc.post()
                .uri(POSTS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(communityPostCommandService);
    }

    @Test
    @DisplayName("[F-29][TR-03] 이메일 인증 전의 회원이 쓰거나 고치거나 지우면 403 MEMBER_NOT_VERIFIED를 응답한다")
    void unverifiedMemberCannotWrite() {
        // when
        MvcTestResult write = send(mvc.post().uri(POSTS), unverified(), VALID_BODY);
        MvcTestResult update = send(mvc.patch().uri(POSTS + "/9"), unverified(), "{}");
        MvcTestResult delete =
                mvc.delete().uri(POSTS + "/9").with(unverified()).with(csrf()).exchange();

        // then
        assertThat(write).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(write).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(update).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(update).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(communityPostCommandService);
    }

    @Test
    @DisplayName("[F-29][CM-03] 글을 고치면 200과 고친 글 상세를 응답하고, 보낸 필드와 로그인한 회원의 ID를 서비스에 넘긴다")
    void updateReturnsRevisedDetail() {
        // given
        when(communityPostCommandService.revise(eq(MEMBER_ID), eq(9L), any())).thenReturn(ITEM);

        // when
        MvcTestResult result = send(
                mvc.patch().uri(POSTS + "/9"),
                verified(),
                "{\"title\":\"새 제목\",\"category\":\"FREE\",\"spotId\":null}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.postId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.updatedAt").isEqualTo("2026-10-05T04:00:00Z");
        ArgumentCaptor<CommunityPostReviseCommand> captor = ArgumentCaptor.forClass(CommunityPostReviseCommand.class);
        verify(communityPostCommandService).revise(eq(MEMBER_ID), eq(9L), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new CommunityPostReviseCommand(
                        PatchField.of(CommunityCategory.FREE),
                        PatchField.of("새 제목"),
                        PatchField.absent(),
                        PatchField.of(null)));
    }

    @Test
    @DisplayName("[F-29][CM-03] 빈 JSON 객체로 고치면 모든 필드가 요청에 없는 것으로 서비스에 넘어간다")
    void updateWithEmptyObjectPassesAbsentFields() {
        // given
        when(communityPostCommandService.revise(eq(MEMBER_ID), eq(9L), any())).thenReturn(ITEM);

        // when
        MvcTestResult result = send(mvc.patch().uri(POSTS + "/9"), verified(), "{}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(communityPostCommandService)
                .revise(
                        MEMBER_ID,
                        9L,
                        new CommunityPostReviseCommand(
                                PatchField.absent(), PatchField.absent(), PatchField.absent(), PatchField.absent()));
    }

    @Test
    @DisplayName("[F-29][CM-03] 수정에서 category가 알 수 없는 값이면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void updateRejectsUnknownCategory() {
        // when
        MvcTestResult result = send(mvc.patch().uri(POSTS + "/9"), verified(), "{\"category\":\"CAMPING\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(communityPostCommandService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{\"category\":null}", "{\"title\":null}", "{\"content\":null}"})
    @DisplayName("[F-29][CM-03] category, title, content를 null로 보내면 서비스가 던진 INVALID_INPUT을 400으로 응답한다")
    void updateWithExplicitNullIsBadRequest(String body) {
        // given
        when(communityPostCommandService.revise(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT, "비울 수 없습니다."));

        // when
        MvcTestResult result = send(mvc.patch().uri(POSTS + "/9"), verified(), body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        ArgumentCaptor<CommunityPostReviseCommand> captor = ArgumentCaptor.forClass(CommunityPostReviseCommand.class);
        verify(communityPostCommandService).revise(eq(MEMBER_ID), eq(9L), captor.capture());
        CommunityPostReviseCommand command = captor.getValue();
        long explicitNulls = List.of(command.category(), command.title(), command.content()).stream()
                .filter(field -> field.present() && field.value() == null)
                .count();
        assertThat(explicitNulls).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-29][CM-03] 다른 회원의 글을 고치거나 지우면 403 ACCESS_DENIED를, 없는 글이면 404 NOT_FOUND를 응답한다")
    void updateAndDeletePropagateAuthorizationAndMissing() {
        // given
        when(communityPostCommandService.revise(anyLong(), eq(9L), any()))
                .thenThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));
        when(communityPostCommandService.revise(anyLong(), eq(8L), any()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        doThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .when(communityPostCommandService)
                .delete(anyLong(), eq(9L));

        // when
        MvcTestResult updateOthers = send(mvc.patch().uri(POSTS + "/9"), verified(), "{}");
        MvcTestResult updateMissing = send(mvc.patch().uri(POSTS + "/8"), verified(), "{}");
        MvcTestResult deleteOthers =
                mvc.delete().uri(POSTS + "/9").with(verified()).with(csrf()).exchange();

        // then
        assertThat(updateOthers).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(updateOthers).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(updateMissing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(deleteOthers).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("[F-29][CM-03] 글을 지우면 204를 본문 없이 응답하고, 로그인한 회원의 ID로 서비스를 부른다")
    void deleteReturnsNoContent() {
        // when
        MvcTestResult result =
                mvc.delete().uri(POSTS + "/9").with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(result).body().isEmpty();
        verify(communityPostCommandService).delete(MEMBER_ID, 9L);
    }

    @Test
    @DisplayName("[F-29] 목록 파라미터를 보내지 않으면 category와 spotId는 거르지 않고 page 0, size 20으로 서비스를 부른다")
    void listWithoutParametersUsesDefaults() {
        // given
        when(communityPostQueryService.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new CommunityPostPage(List.of(), 0, 20, false));

        // when
        MvcTestResult result = mvc.get().uri(POSTS).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(communityPostQueryService).list(null, null, 0, 20);
    }

    private static String body(String title, String content) {
        return "{\"category\":\"FREE\",\"title\":\"" + title + "\",\"content\":\"" + content + "\"}";
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
