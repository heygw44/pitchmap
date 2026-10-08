package com.pitchmap.trust.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import com.pitchmap.trust.application.CompanionReviewCommandService;
import com.pitchmap.trust.application.CompanionReviewPage;
import com.pitchmap.trust.application.CompanionReviewQueryService;
import com.pitchmap.trust.application.CompanionReviewWriteCommand;
import com.pitchmap.trust.application.PendingCompanionReview;
import com.pitchmap.trust.application.PublicCompanionReview;
import com.pitchmap.trust.application.ReceivedCompanionReview;
import com.pitchmap.trust.domain.CompanionReviewTag;
import com.pitchmap.trust.domain.TrustErrorCode;
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

@WebMvcTest(CompanionReviewController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class CompanionReviewControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final long BASECAMP_ID = 21L;
    private static final String WRITE_URI = "/api/basecamps/" + BASECAMP_ID + "/companion-reviews";
    private static final String RECEIVED_URI = "/api/me/companion-reviews/received";
    private static final String PENDING_URI = "/api/me/companion-reviews/pending";
    private static final String MEMBER_URI = "/api/members/31/companion-reviews";
    private static final String VALID_BODY =
            "{\"revieweeId\":31,\"rejoinWanted\":true,\"tags\":[\"ON_TIME\",\"LEAVE_NO_TRACE\"],\"comment\":\"좋았다\"}";
    private static final Instant COMPLETED_AT = Instant.parse("2026-10-05T03:00:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-10-06T03:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CompanionReviewCommandService commandService;

    @MockitoBean
    private CompanionReviewQueryService queryService;

    @Test
    @DisplayName("[F-15] 후기를 쓰면 201과 reviewId를 응답하고, 요청 값과 로그인한 회원의 ID를 서비스에 넘긴다")
    void writeReturnsCreatedWithReviewId() {
        // given
        when(commandService.write(eq(MEMBER_ID), eq(BASECAMP_ID), any())).thenReturn(55L);

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"reviewId\": 55 }");
        ArgumentCaptor<CompanionReviewWriteCommand> captor = ArgumentCaptor.forClass(CompanionReviewWriteCommand.class);
        verify(commandService).write(eq(MEMBER_ID), eq(BASECAMP_ID), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new CompanionReviewWriteCommand(
                        31L, true, List.of(CompanionReviewTag.ON_TIME, CompanionReviewTag.LEAVE_NO_TRACE), "좋았다"));
    }

    @Test
    @DisplayName("[F-15] 태그와 코멘트는 생략할 수 있다")
    void writeAllowsMissingTagsAndComment() {
        // given
        when(commandService.write(anyLong(), anyLong(), any())).thenReturn(1L);

        // when
        MvcTestResult result = post(verified(), "{\"revieweeId\":31,\"rejoinWanted\":false}");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        ArgumentCaptor<CompanionReviewWriteCommand> captor = ArgumentCaptor.forClass(CompanionReviewWriteCommand.class);
        verify(commandService).write(anyLong(), anyLong(), captor.capture());
        assertThat(captor.getValue().tags()).isEmpty();
        assertThat(captor.getValue().comment()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{\"rejoinWanted\":true}",
                "{\"revieweeId\":31}",
                "{\"revieweeId\":0,\"rejoinWanted\":true}",
                "{\"revieweeId\":-1,\"rejoinWanted\":true}",
                "{\"revieweeId\":31,\"rejoinWanted\":true,\"tags\":[\"ON_TIME\",\"ON_TIME\"]}",
                "{\"revieweeId\":31,\"rejoinWanted\":true,\"tags\":[\"UNKNOWN\"]}",
                "{\"revieweeId\":31,\"rejoinWanted\":true,\"tags\":[null]}",
                "{\"revieweeId\":31,\"rejoinWanted\":true,\"tags\":[\"ON_TIME\",\"LEAVE_NO_TRACE\",\"CONSIDERATE\","
                        + "\"WELL_PREPARED\",\"LATE\",\"NO_SHOW\",\"LITTERING\",\"ON_TIME\"]}"
            })
    @DisplayName("[F-15] 상대나 다시 동행 여부가 없거나, 태그가 중복이거나 잘못된 값이거나 7개를 넘으면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void writeRejectsInvalidBody(String body) {
        // when
        MvcTestResult result = post(verified(), body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(commandService);
    }

    @Test
    @DisplayName("[F-15] 같은 태그를 두 번 넣으면 tags 필드 오류를 응답한다")
    void duplicateTagsReportFieldError() {
        // when
        MvcTestResult result =
                post(verified(), "{\"revieweeId\":31,\"rejoinWanted\":true,\"tags\":[\"LATE\",\"LATE\"]}");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='tags')]")
                .asList()
                .hasSize(1);
    }

    @Test
    @DisplayName("[RV-04] 태그가 7개이고 코멘트가 300자이면 받고, 코멘트가 301자이면 comment 필드 오류로 400을 응답한다")
    void writeChecksLimitBoundaries() {
        // given
        when(commandService.write(anyLong(), anyLong(), any())).thenReturn(1L);
        String allTags =
                "[\"ON_TIME\",\"LEAVE_NO_TRACE\",\"CONSIDERATE\",\"WELL_PREPARED\",\"LATE\",\"NO_SHOW\",\"LITTERING\"]";

        // when
        MvcTestResult atLimit = post(
                verified(),
                "{\"revieweeId\":31,\"rejoinWanted\":true,\"tags\":" + allTags + ",\"comment\":\"" + "가".repeat(300)
                        + "\"}");
        MvcTestResult overLimit =
                post(verified(), "{\"revieweeId\":31,\"rejoinWanted\":true,\"comment\":\"" + "가".repeat(301) + "\"}");

        // then
        assertThat(atLimit).hasStatus(HttpStatus.CREATED);
        assertThat(overLimit).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(overLimit)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='comment')]")
                .asList()
                .hasSize(1);
    }

    @Test
    @DisplayName("[F-15] 서비스가 던진 오류 코드를 그대로 상태 코드로 응답한다")
    void writePropagatesServiceErrors() {
        // given
        when(commandService.write(anyLong(), eq(1L), any()))
                .thenThrow(new BusinessException(TrustErrorCode.COMPANION_REVIEW_NOT_ELIGIBLE));
        when(commandService.write(anyLong(), eq(2L), any()))
                .thenThrow(new BusinessException(TrustErrorCode.COMPANION_REVIEW_DEADLINE_PASSED));
        when(commandService.write(anyLong(), eq(3L), any()))
                .thenThrow(new BusinessException(TrustErrorCode.COMPANION_REVIEW_DUPLICATED));
        when(commandService.write(anyLong(), eq(4L), any()))
                .thenThrow(new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT));

        // when
        MvcTestResult notEligible = postTo(verified(), 1L, VALID_BODY);
        MvcTestResult deadline = postTo(verified(), 2L, VALID_BODY);
        MvcTestResult duplicated = postTo(verified(), 3L, VALID_BODY);
        MvcTestResult level = postTo(verified(), 4L, VALID_BODY);

        // then
        assertThat(notEligible).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(notEligible).bodyJson().extractingPath("$.code").isEqualTo("COMPANION_REVIEW_NOT_ELIGIBLE");
        assertThat(deadline).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(deadline).bodyJson().extractingPath("$.code").isEqualTo("COMPANION_REVIEW_DEADLINE_PASSED");
        assertThat(duplicated).hasStatus(HttpStatus.CONFLICT);
        assertThat(duplicated).bodyJson().extractingPath("$.code").isEqualTo("COMPANION_REVIEW_DUPLICATED");
        assertThat(level).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(level).bodyJson().extractingPath("$.code").isEqualTo("TRUST_LEVEL_INSUFFICIENT");
    }

    @Test
    @DisplayName("[F-15] 로그인하지 않은 사용자는 쓰기·작성 목록·받은 후기·회원 후기 모두 401 AUTHENTICATION_REQUIRED다")
    void anonymousIsUnauthorized() {
        // when
        MvcTestResult write = mvc.post()
                .uri(WRITE_URI)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();
        MvcTestResult pending = mvc.get().uri(PENDING_URI).exchange();
        MvcTestResult received = mvc.get().uri(RECEIVED_URI).exchange();
        MvcTestResult member = mvc.get().uri(MEMBER_URI).exchange();

        // then
        for (MvcTestResult result : new MvcTestResult[] {write, pending, received, member}) {
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        }
        verifyNoInteractions(commandService, queryService);
    }

    @Test
    @DisplayName("[F-15] 이메일 인증 전의 회원은 쓰기·작성 목록·회원 후기가 403 MEMBER_NOT_VERIFIED지만, 받은 후기는 볼 수 있다")
    void unverifiedMemberCanOnlyReadReceived() {
        // given
        when(queryService.received(MEMBER_ID, 0, 20)).thenReturn(new CompanionReviewPage<>(List.of(), 0, 20, false));

        // when
        MvcTestResult write = post(unverified(), VALID_BODY);
        MvcTestResult pending = mvc.get().uri(PENDING_URI).with(unverified()).exchange();
        MvcTestResult member = mvc.get().uri(MEMBER_URI).with(unverified()).exchange();
        MvcTestResult received = mvc.get().uri(RECEIVED_URI).with(unverified()).exchange();

        // then
        for (MvcTestResult result : new MvcTestResult[] {write, pending, member}) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        }
        assertThat(received).hasStatus(HttpStatus.OK);
        verifyNoInteractions(commandService);
    }

    @Test
    @DisplayName("[F-15] 작성할 후기 목록은 배열로 응답한다")
    void pendingReturnsArray() {
        // given
        when(queryService.pending(MEMBER_ID))
                .thenReturn(List.of(new PendingCompanionReview(
                        BASECAMP_ID,
                        "굴업도 주말 1박",
                        COMPLETED_AT,
                        COMPLETED_AT.plusSeconds(14 * 24 * 3600L),
                        List.of(new PendingCompanionReview.Target(31L, "새벽능선")))));

        // when
        MvcTestResult result = mvc.get().uri(PENDING_URI).with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                [{
                  "basecampId": 21,
                  "basecampTitle": "굴업도 주말 1박",
                  "completedAt": "2026-10-05T03:00:00Z",
                  "deadline": "2026-10-19T03:00:00Z",
                  "targets": [{ "memberId": 31, "nickname": "새벽능선" }]
                }]
                """);
    }

    @Test
    @DisplayName("[RV-03] 받은 후기는 공개된 항목에 모든 필드를, 공개 전 항목에는 basecampId와 revealed만 담는다")
    void receivedOmitsFieldsOfSealedItem() {
        // given
        ReceivedCompanionReview revealed = new ReceivedCompanionReview(
                21L, true, 9L, "굴업도 주말 1박", 31L, "새벽능선", true, List.of(CompanionReviewTag.ON_TIME), "좋았다", CREATED_AT);
        ReceivedCompanionReview sealed = sealedItem(22L);
        when(queryService.received(MEMBER_ID, 0, 20))
                .thenReturn(new CompanionReviewPage<>(List.of(revealed, sealed), 0, 20, true));

        // when
        MvcTestResult result = mvc.get().uri(RECEIVED_URI).with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [
                    {
                      "reviewId": 9,
                      "basecampId": 21,
                      "basecampTitle": "굴업도 주말 1박",
                      "reviewer": { "memberId": 31, "nickname": "새벽능선" },
                      "rejoinWanted": true,
                      "tags": ["ON_TIME"],
                      "comment": "좋았다",
                      "createdAt": "2026-10-06T03:00:00Z",
                      "revealed": true
                    },
                    { "basecampId": 22, "revealed": false }
                  ],
                  "page": 0, "size": 20, "hasNext": true
                }
                """);
    }

    @Test
    @DisplayName("[RV-06] 회원이 받은 후기 항목은 tags, comment, createdAt뿐이고 page와 size를 서비스에 넘긴다")
    void memberReviewsHaveOnlyPublicFields() {
        // given
        when(queryService.forMember(31L, 1, 5))
                .thenReturn(new CompanionReviewPage<>(
                        List.of(new PublicCompanionReview(List.of(CompanionReviewTag.LATE), "늦었다", CREATED_AT)),
                        1,
                        5,
                        false));

        // when
        MvcTestResult result =
                mvc.get().uri(MEMBER_URI + "?page=1&size=5").with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{ "tags": ["LATE"], "comment": "늦었다", "createdAt": "2026-10-06T03:00:00Z" }],
                  "page": 1, "size": 5, "hasNext": false
                }
                """);
    }

    @Test
    @DisplayName("[F-15] 없는 회원의 후기를 조회하면 서비스가 던진 NOT_FOUND를 404로 응답한다")
    void memberReviewsOfMissingMemberIsNotFound() {
        // given
        when(queryService.forMember(anyLong(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult result = mvc.get().uri(MEMBER_URI).with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"?size=51", "?size=0", "?page=-1"})
    @DisplayName("[F-15] 목록의 size나 page가 범위를 벗어나면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void listsRejectOutOfRangePaging(String query) {
        // when
        MvcTestResult received =
                mvc.get().uri(RECEIVED_URI + query).with(verified()).exchange();
        MvcTestResult member =
                mvc.get().uri(MEMBER_URI + query).with(verified()).exchange();

        // then
        assertThat(received).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(received).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(member).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("[RV-05] 후기를 고치거나 지우는 요청은 컨트롤러에 닿지 않고 거부된다")
    void patchAndDeleteAreRejected() {
        // when
        MvcTestResult patch = mvc.patch()
                .uri(WRITE_URI + "/9")
                .with(verified())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();
        MvcTestResult delete =
                mvc.delete().uri(WRITE_URI + "/9").with(verified()).with(csrf()).exchange();
        MvcTestResult patchOnCollection = mvc.patch()
                .uri(WRITE_URI)
                .with(verified())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(patch.getResponse().getStatus()).isIn(404, 405);
        assertThat(delete.getResponse().getStatus()).isIn(404, 405);
        assertThat(patchOnCollection.getResponse().getStatus()).isIn(403, 404, 405);
        verifyNoInteractions(commandService, queryService);
    }

    // 공개 전 항목을 만드는 정적 팩터리는 application 패키지 안에서만 보이므로, 같은 모양의 값을 생성자로 직접 만든다.
    private static ReceivedCompanionReview sealedItem(long basecampId) {
        return new ReceivedCompanionReview(basecampId, false, null, null, null, null, null, null, null, null);
    }

    private MvcTestResult post(RequestPostProcessor login, String body) {
        return postTo(login, BASECAMP_ID, body);
    }

    private MvcTestResult postTo(RequestPostProcessor login, long basecampId, String body) {
        return mvc.post()
                .uri("/api/basecamps/" + basecampId + "/companion-reviews")
                .with(login)
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
