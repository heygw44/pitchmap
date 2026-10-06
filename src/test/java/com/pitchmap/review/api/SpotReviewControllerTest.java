package com.pitchmap.review.api;

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
import com.pitchmap.review.application.SpotReviewCommandService;
import com.pitchmap.review.application.SpotReviewItem;
import com.pitchmap.review.application.SpotReviewPage;
import com.pitchmap.review.application.SpotReviewQueryService;
import com.pitchmap.review.application.SpotReviewReviseCommand;
import com.pitchmap.review.application.SpotReviewWriteCommand;
import com.pitchmap.review.domain.ReviewErrorCode;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(SpotReviewController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class SpotReviewControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final long SPOT_ID = 101L;
    private static final String SPOT_REVIEWS = "/api/spots/" + SPOT_ID + "/reviews";
    private static final String VALID_BODY = "{\"visitedDate\":\"2026-10-04\",\"rating\":4,\"content\":\"물이 가까웠다\"}";
    private static final String VALID_UPDATE_BODY = "{\"rating\":5,\"content\":\"다시 가고 싶다\"}";
    private static final SpotReviewItem ITEM = new SpotReviewItem(
            9L, 31L, "새벽능선", LocalDate.of(2026, 10, 4), 5, "별이 좋았다", Instant.parse("2026-10-05T03:00:00Z"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SpotReviewCommandService spotReviewCommandService;

    @MockitoBean
    private SpotReviewQueryService spotReviewQueryService;

    @Test
    @DisplayName("[F-10] 로그인하지 않은 사용자도 후기 목록을 조회하면 항목의 모든 필드와 페이지 정보를 받는다")
    void anonymousReadsReviewList() {
        // given
        when(spotReviewQueryService.list(SPOT_ID, 0, 20)).thenReturn(new SpotReviewPage(List.of(ITEM), 0, 20, true));

        // when
        MvcTestResult result = mvc.get().uri(SPOT_REVIEWS).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "reviewId": 9,
                    "author": { "memberId": 31, "nickname": "새벽능선" },
                    "visitedDate": "2026-10-04",
                    "rating": 5,
                    "content": "별이 좋았다",
                    "createdAt": "2026-10-05T03:00:00Z"
                  }],
                  "page": 0, "size": 20, "hasNext": true
                }
                """);
    }

    @Test
    @DisplayName("[F-10] 목록의 page와 size를 서비스에 그대로 넘긴다")
    void listPassesPageAndSize() {
        // given
        when(spotReviewQueryService.list(SPOT_ID, 2, 50)).thenReturn(new SpotReviewPage(List.of(), 2, 50, false));

        // when
        MvcTestResult result = mvc.get().uri(SPOT_REVIEWS + "?page=2&size=50").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(2);
        verify(spotReviewQueryService).list(SPOT_ID, 2, 50);
    }

    @Test
    @DisplayName("[F-10] 목록의 size가 51이거나 page가 음수이면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void listRejectsOutOfRangePaging() {
        // when
        MvcTestResult tooBig = mvc.get().uri(SPOT_REVIEWS + "?size=51").exchange();
        MvcTestResult negativePage = mvc.get().uri(SPOT_REVIEWS + "?page=-1").exchange();

        // then
        assertThat(tooBig).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooBig).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(negativePage).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(spotReviewQueryService);
    }

    @Test
    @DisplayName("[F-10] 없거나 ACTIVE가 아닌 장소의 후기 목록은 서비스가 던진 NOT_FOUND를 404로 응답한다")
    void listOfMissingSpotIsNotFound() {
        // given
        when(spotReviewQueryService.list(anyLong(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult result = mvc.get().uri(SPOT_REVIEWS).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-10] 후기를 쓰면 201과 reviewId를 응답하고, 요청 값과 로그인한 회원의 ID를 서비스에 넘긴다")
    void writeReturnsCreatedWithReviewId() {
        // given
        when(spotReviewCommandService.write(eq(MEMBER_ID), eq(SPOT_ID), any())).thenReturn(55L);

        // when
        MvcTestResult result = post(verified(), SPOT_REVIEWS, VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"reviewId\": 55 }");
        ArgumentCaptor<SpotReviewWriteCommand> captor = ArgumentCaptor.forClass(SpotReviewWriteCommand.class);
        verify(spotReviewCommandService).write(eq(MEMBER_ID), eq(SPOT_ID), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new SpotReviewWriteCommand(LocalDate.of(2026, 10, 4), 4, "물이 가까웠다"));
    }

    @Test
    @DisplayName("[F-10] 같은 장소·방문일 후기가 이미 있으면 서비스가 던진 SPOT_REVIEW_DUPLICATED를 409로 응답한다")
    void duplicatedReviewIsConflict() {
        // given
        when(spotReviewCommandService.write(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(ReviewErrorCode.SPOT_REVIEW_DUPLICATED));

        // when
        MvcTestResult result = post(verified(), SPOT_REVIEWS, VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("SPOT_REVIEW_DUPLICATED");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{\"rating\":4,\"content\":\"좋다\"}",
                "{\"visitedDate\":\"2026-10-04\",\"content\":\"좋다\"}",
                "{\"visitedDate\":\"2026-10-04\",\"rating\":0,\"content\":\"좋다\"}",
                "{\"visitedDate\":\"2026-10-04\",\"rating\":6,\"content\":\"좋다\"}",
                "{\"visitedDate\":\"2026-10-04\",\"rating\":4}",
                "{\"visitedDate\":\"2026-10-04\",\"rating\":4,\"content\":\"   \"}",
                "{\"visitedDate\":\"날짜 아님\",\"rating\":4,\"content\":\"좋다\"}"
            })
    @DisplayName("[F-10] 방문일이 없거나 형식이 틀리거나, 평점이 1~5가 아니거나, 내용이 비면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void writeRejectsInvalidBody(String body) {
        // when
        MvcTestResult result = post(verified(), SPOT_REVIEWS, body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(spotReviewCommandService);
    }

    @Test
    @DisplayName("[F-10] 후기 내용이 2000자이면 받고, 2001자이면 content 필드 오류로 400을 응답한다")
    void writeChecksContentLengthBoundary() {
        // given
        when(spotReviewCommandService.write(anyLong(), anyLong(), any())).thenReturn(1L);

        // when
        MvcTestResult atLimit = post(verified(), SPOT_REVIEWS, bodyWithContent("가".repeat(2000)));
        MvcTestResult overLimit = post(verified(), SPOT_REVIEWS, bodyWithContent("가".repeat(2001)));

        // then
        assertThat(atLimit).hasStatus(HttpStatus.CREATED);
        assertThat(overLimit).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(overLimit)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='content')]")
                .asList()
                .hasSize(1);
    }

    @Test
    @DisplayName("[F-10] 로그인하지 않은 사용자가 쓰면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousWriteIsUnauthorized() {
        // when
        MvcTestResult result = mvc.post()
                .uri(SPOT_REVIEWS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(spotReviewCommandService);
    }

    @Test
    @DisplayName("[F-10] 이메일 인증 전의 회원이 쓰거나 고치거나 지우면 403 MEMBER_NOT_VERIFIED를 응답한다")
    void unverifiedMemberCannotWrite() {
        // when
        MvcTestResult write = post(unverified(), SPOT_REVIEWS, VALID_BODY);
        MvcTestResult update = patch(unverified(), 9L, VALID_UPDATE_BODY);
        MvcTestResult delete = delete(unverified(), 9L);

        // then
        assertThat(write).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(write).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(update).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(update).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(spotReviewCommandService);
    }

    @Test
    @DisplayName("[F-10] 후기를 고치면 200과 고친 후기 항목을 응답하고, 평점과 내용을 서비스에 넘긴다")
    void updateReturnsRevisedItem() {
        // given
        when(spotReviewCommandService.revise(eq(MEMBER_ID), eq(9L), any())).thenReturn(ITEM);

        // when
        MvcTestResult result = patch(verified(), 9L, VALID_UPDATE_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.reviewId").isEqualTo(9);
        assertThat(result).bodyJson().extractingPath("$.author.nickname").isEqualTo("새벽능선");
        assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-10-05T03:00:00Z");
        ArgumentCaptor<SpotReviewReviseCommand> captor = ArgumentCaptor.forClass(SpotReviewReviseCommand.class);
        verify(spotReviewCommandService).revise(eq(MEMBER_ID), eq(9L), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new SpotReviewReviseCommand(5, "다시 가고 싶다"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{\"content\":\"좋다\"}",
                "{\"rating\":4}",
                "{\"rating\":6,\"content\":\"좋다\"}",
                "{\"rating\":4,\"content\":\"\"}"
            })
    @DisplayName("[F-10] 수정은 평점과 내용을 둘 다 보내야 하고, 값이 규칙을 어기면 400 INVALID_INPUT을 응답한다")
    void updateRejectsInvalidBody(String body) {
        // when
        MvcTestResult result = patch(verified(), 9L, body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(spotReviewCommandService);
    }

    @Test
    @DisplayName("[F-10] 다른 회원의 후기를 고치거나 지우면 서비스가 던진 ACCESS_DENIED를 403으로, 없는 후기이면 NOT_FOUND를 404로 응답한다")
    void updateAndDeletePropagateAuthorizationAndMissing() {
        // given
        when(spotReviewCommandService.revise(anyLong(), eq(9L), any()))
                .thenThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));
        when(spotReviewCommandService.revise(anyLong(), eq(8L), any()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));
        doThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .when(spotReviewCommandService)
                .delete(anyLong(), eq(9L));

        // when
        MvcTestResult updateOthers = patch(verified(), 9L, VALID_UPDATE_BODY);
        MvcTestResult updateMissing = patch(verified(), 8L, VALID_UPDATE_BODY);
        MvcTestResult deleteOthers = delete(verified(), 9L);

        // then
        assertThat(updateOthers).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(updateOthers).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(updateMissing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(deleteOthers).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("[F-10] 후기를 지우면 204를 본문 없이 응답하고, 로그인한 회원의 ID로 서비스를 부른다")
    void deleteReturnsNoContent() {
        // when
        MvcTestResult result = delete(verified(), 9L);

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(result).body().isEmpty();
        verify(spotReviewCommandService).delete(MEMBER_ID, 9L);
    }

    private static String bodyWithContent(String content) {
        return "{\"visitedDate\":\"2026-10-04\",\"rating\":4,\"content\":\"" + content + "\"}";
    }

    private MvcTestResult post(RequestPostProcessor login, String uri, String body) {
        return mvc.post()
                .uri(uri)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult patch(RequestPostProcessor login, long reviewId, String body) {
        return mvc.patch()
                .uri("/api/reviews/" + reviewId)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult delete(RequestPostProcessor login, long reviewId) {
        return mvc.delete()
                .uri("/api/reviews/" + reviewId)
                .with(login)
                .with(csrf())
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
