package com.pitchmap.basecamp.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.pitchmap.basecamp.application.BasecampApplicationCancelService;
import com.pitchmap.basecamp.application.BasecampApplicationListQuery;
import com.pitchmap.basecamp.application.BasecampApplicationListService;
import com.pitchmap.basecamp.application.BasecampApplicationPage;
import com.pitchmap.basecamp.application.BasecampApplyCommand;
import com.pitchmap.basecamp.application.BasecampApplyResult;
import com.pitchmap.basecamp.application.BasecampApplyService;
import com.pitchmap.basecamp.application.BasecampApprovalService;
import com.pitchmap.basecamp.application.BasecampApproveResult;
import com.pitchmap.basecamp.application.BasecampDetail;
import com.pitchmap.basecamp.application.BasecampDetailQueryService;
import com.pitchmap.basecamp.application.BasecampMembershipService;
import com.pitchmap.basecamp.application.BasecampOpenCommand;
import com.pitchmap.basecamp.application.BasecampOpenCommand.JoinConditionCommand;
import com.pitchmap.basecamp.application.BasecampOpenResult;
import com.pitchmap.basecamp.application.BasecampOpenService;
import com.pitchmap.basecamp.application.BasecampRejectResult;
import com.pitchmap.basecamp.application.BasecampSearchItem;
import com.pitchmap.basecamp.application.BasecampSearchPage;
import com.pitchmap.basecamp.application.BasecampSearchQuery;
import com.pitchmap.basecamp.application.BasecampSearchService;
import com.pitchmap.basecamp.application.JoinConditionSummary;
import com.pitchmap.basecamp.application.JoinEligibility;
import com.pitchmap.basecamp.domain.BasecampApplicationStatus;
import com.pitchmap.basecamp.domain.BasecampErrorCode;
import com.pitchmap.basecamp.domain.BasecampException;
import com.pitchmap.basecamp.domain.BasecampRelation;
import com.pitchmap.basecamp.domain.JoinUnmetReason;
import com.pitchmap.basecamp.domain.KickReason;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.GlobalExceptionHandler;
import com.pitchmap.common.security.LoginMember;
import com.pitchmap.common.security.SecurityConfig;
import com.pitchmap.common.trace.TraceIdFilter;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

@WebMvcTest(BasecampController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class BasecampControllerTest {

    private static final long MEMBER_ID = 7L;
    private static final String BASECAMPS = "/api/basecamps";
    private static final String APPLICATIONS = "/api/basecamps/77/applications";
    private static final String APPROVE = "/api/basecamps/77/applications/501/approve";
    private static final String REJECT = "/api/basecamps/77/applications/501/reject";
    private static final String LEAVE = "/api/basecamps/77/members/me";
    private static final String KICK = "/api/basecamps/77/members/31/kick";
    private static final String MY_APPLICATION = "/api/basecamps/77/applications/me";
    private static final String VALID_BODY = body("\"title\":\"북한산 백패킹\"", "\"capacity\":4");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BasecampOpenService basecampOpenService;

    @MockitoBean
    private BasecampSearchService basecampSearchService;

    @MockitoBean
    private BasecampDetailQueryService basecampDetailQueryService;

    @MockitoBean
    private BasecampApplyService basecampApplyService;

    @MockitoBean
    private BasecampApplicationCancelService basecampApplicationCancelService;

    @MockitoBean
    private BasecampApplicationListService basecampApplicationListService;

    @MockitoBean
    private BasecampApprovalService basecampApprovalService;

    @MockitoBean
    private BasecampMembershipService basecampMembershipService;

    @Test
    @DisplayName("[F-12] 베이스캠프를 열면 201과 basecampId·status를 응답하고, 요청 값과 로그인한 회원의 ID를 서비스에 넘긴다")
    void openReturnsCreated() {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenReturn(new BasecampOpenResult(55L, "RECRUITING"));

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"basecampId\": 55, \"status\": \"RECRUITING\" }");
        ArgumentCaptor<BasecampOpenCommand> captor = ArgumentCaptor.forClass(BasecampOpenCommand.class);
        verify(basecampOpenService).open(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new BasecampOpenCommand(
                        101L, "북한산 백패킹", "함께 가요", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), 4, null));
    }

    @Test
    @DisplayName("[F-12][BC-05] 합류 조건을 보내면 그대로 서비스에 넘긴다")
    void joinConditionIsPassedToService() {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenReturn(new BasecampOpenResult(55L, "RECRUITING"));
        String joinCondition =
                "\"joinCondition\":{\"minTrustLevel\":2,\"ageGroupMin\":20,\"ageGroupMax\":30,\"sameGenderOnly\":true}";

        // when
        MvcTestResult result = post(verified(), body("\"title\":\"북한산 백패킹\"", "\"capacity\":4", joinCondition));

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        ArgumentCaptor<BasecampOpenCommand> captor = ArgumentCaptor.forClass(BasecampOpenCommand.class);
        verify(basecampOpenService).open(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue().joinCondition()).isEqualTo(new JoinConditionCommand(2, 20, 30, true));
    }

    @Test
    @DisplayName("[F-12] 제목이 100자이면 받고, 101자이면 title 필드 오류로 400 INVALID_INPUT을 응답한다")
    void titleLengthBoundary() {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenReturn(new BasecampOpenResult(1L, "RECRUITING"));

        // when
        MvcTestResult atLimit = post(verified(), body("\"title\":\"" + "가".repeat(100) + "\"", "\"capacity\":4"));
        MvcTestResult overLimit = post(verified(), body("\"title\":\"" + "가".repeat(101) + "\"", "\"capacity\":4"));

        // then
        assertThat(atLimit).hasStatus(HttpStatus.CREATED);
        assertThat(overLimit).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(overLimit).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(overLimit)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='title')]")
                .asList()
                .hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bodiesWithMissingRequiredValue")
    @DisplayName("[F-12] 필수 값이 없거나 비어 있으면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void openRejectsMissingRequiredValue(String description, String requestBody) {
        // when
        MvcTestResult result = post(verified(), requestBody);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(basecampOpenService);
    }

    static Stream<Arguments> bodiesWithMissingRequiredValue() {
        return Stream.of(
                Arguments.of(
                        "장소 없음",
                        "{" + String.join(",", "\"title\":\"가\"", "\"description\":\"나\"", dates(), "\"capacity\":4")
                                + "}"),
                Arguments.of(
                        "제목 없음",
                        "{" + String.join(",", "\"spotId\":101", "\"description\":\"나\"", dates(), "\"capacity\":4")
                                + "}"),
                Arguments.of("제목 공백", body("\"title\":\"   \"", "\"capacity\":4")),
                Arguments.of(
                        "설명 없음",
                        "{" + String.join(",", "\"spotId\":101", "\"title\":\"가\"", dates(), "\"capacity\":4") + "}"),
                Arguments.of(
                        "출발일 없음",
                        "{"
                                + String.join(
                                        ",",
                                        "\"spotId\":101",
                                        "\"title\":\"가\"",
                                        "\"description\":\"나\"",
                                        "\"endDate\":\"2026-10-22\"",
                                        "\"capacity\":4")
                                + "}"),
                Arguments.of(
                        "종료일 없음",
                        "{"
                                + String.join(
                                        ",",
                                        "\"spotId\":101",
                                        "\"title\":\"가\"",
                                        "\"description\":\"나\"",
                                        "\"startDate\":\"2026-10-20\"",
                                        "\"capacity\":4")
                                + "}"),
                Arguments.of(
                        "정원 없음",
                        "{" + String.join(",", "\"spotId\":101", "\"title\":\"가\"", "\"description\":\"나\"", dates())
                                + "}"));
    }

    @Test
    @DisplayName("[F-12] 로그인하지 않은 사용자가 열면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousIsUnauthorized() {
        // when
        MvcTestResult result = mvc.post()
                .uri(BASECAMPS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(basecampOpenService);
    }

    @Test
    @DisplayName("[F-12][TR-03] 이메일 인증 전의 회원이 열면 403 MEMBER_NOT_VERIFIED를 응답한다")
    void unverifiedMemberIsForbidden() {
        // when
        MvcTestResult result = post(unverified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampOpenService);
    }

    @Test
    @DisplayName("[F-12] CSRF 토큰 없이 열면 403을 응답하고 서비스를 부르지 않는다")
    void missingCsrfIsForbidden() {
        // when
        MvcTestResult result = mvc.post()
                .uri(BASECAMPS)
                .with(verified())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(basecampOpenService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("serviceErrors")
    @DisplayName("[F-12] 서비스가 던진 오류는 코드와 상태 그대로 응답한다")
    void serviceErrorsAreMapped(BusinessException error, HttpStatus status) {
        // given
        when(basecampOpenService.open(eq(MEMBER_ID), any())).thenThrow(error);

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(status);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo(error.getErrorCode().name());
    }

    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT), HttpStatus.FORBIDDEN),
                Arguments.of(new BusinessException(CommonErrorCode.NOT_FOUND), HttpStatus.NOT_FOUND),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_OPEN_LIMIT), HttpStatus.BAD_REQUEST),
                Arguments.of(
                        new BasecampException(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID), HttpStatus.BAD_REQUEST),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_WARNING_SPOT), HttpStatus.BAD_REQUEST),
                Arguments.of(
                        new BasecampException(BasecampErrorCode.BASECAMP_CAPACITY_INVALID), HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSearchParameters")
    @DisplayName("[F-12] 검색 파라미터가 어긋나면 400 INVALID_INPUT을 응답하고 서비스를 부르지 않는다")
    void searchRejectsInvalidParameters(String description, String query) {
        // when
        MvcTestResult result = mvc.get().uri(BASECAMPS + "?" + query).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(basecampSearchService);
    }

    static Stream<Arguments> invalidSearchParameters() {
        String area = "swLat=37.0&swLng=127.0&neLat=38.0&neLng=128.0";
        String radius = "lat=37.25&lng=127.25&radiusKm=10";
        return Stream.of(
                Arguments.of("지역 없음", "page=0"),
                Arguments.of("영역 일부만", "swLat=37.0&swLng=127.0"),
                Arguments.of("반경 일부만", "lat=37.25&lng=127.25"),
                Arguments.of("영역과 반경 모두", area + "&" + radius),
                Arguments.of("영역과 반경 일부", area + "&radiusKm=10"),
                Arguments.of("반경 50km 초과", "lat=37.25&lng=127.25&radiusKm=50.1"),
                Arguments.of("반경 0", "lat=37.25&lng=127.25&radiusKm=0"),
                Arguments.of("위도 범위 밖", "lat=91&lng=127.25&radiusKm=10"),
                Arguments.of("남서쪽 위도가 북동쪽 이상", "swLat=38.0&swLng=127.0&neLat=37.0&neLng=128.0"),
                Arguments.of("남서쪽 경도가 북동쪽 이상", "swLat=37.0&swLng=128.0&neLat=38.0&neLng=127.0"),
                Arguments.of("출발일 범위 역순", radius + "&fromDate=2026-10-25&toDate=2026-10-20"),
                Arguments.of("날짜 형식 오류", radius + "&fromDate=20261020"),
                Arguments.of("페이지 크기 51", radius + "&size=51"),
                Arguments.of("페이지 크기 0", radius + "&size=0"),
                Arguments.of("페이지 번호 음수", radius + "&page=-1"));
    }

    @Test
    @DisplayName("[F-12] 반경 50km와 출발일 범위를 보내면 받아서 기본값(page 0, size 20)과 함께 서비스에 넘긴다")
    void searchPassesRadiusQueryWithDefaults() {
        // given
        when(basecampSearchService.search(any(), any())).thenReturn(new BasecampSearchPage(List.of(), 0, 20, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(BASECAMPS
                        + "?lat=37.25&lng=127.25&radiusKm=50&fromDate=2026-10-20&toDate=2026-10-25&hasVacancy=true")
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        ArgumentCaptor<BasecampSearchQuery> captor = ArgumentCaptor.forClass(BasecampSearchQuery.class);
        verify(basecampSearchService).search(eq(null), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new BasecampSearchQuery(
                        new BasecampSearchQuery.Radius(37.25, 127.25, 50.0),
                        LocalDate.of(2026, 10, 20),
                        LocalDate.of(2026, 10, 25),
                        true,
                        0,
                        20));
    }

    @Test
    @DisplayName("[F-12] 지도 영역을 보내면 영역 조건으로 서비스에 넘기고, 로그인한 회원의 ID를 함께 넘긴다")
    void searchPassesAreaQueryWithViewerId() {
        // given
        when(basecampSearchService.search(any(), any())).thenReturn(new BasecampSearchPage(List.of(), 1, 50, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(BASECAMPS + "?swLat=37.0&swLng=127.0&neLat=38.0&neLng=128.0&page=1&size=50")
                .with(verified())
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        ArgumentCaptor<BasecampSearchQuery> captor = ArgumentCaptor.forClass(BasecampSearchQuery.class);
        verify(basecampSearchService).search(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new BasecampSearchQuery(
                        new BasecampSearchQuery.Area(37.0, 127.0, 38.0, 128.0), null, null, false, 1, 50));
    }

    @Test
    @DisplayName("[F-12][NFR-11] 비로그인 검색 응답에는 canApply와 unmetReasons 필드가 없다")
    void anonymousSearchOmitsEligibilityFields() {
        // given
        when(basecampSearchService.search(any(), any()))
                .thenReturn(new BasecampSearchPage(List.of(searchItem(null)), 0, 20, true));

        // when
        MvcTestResult result =
                mvc.get().uri(BASECAMPS + "?lat=37.25&lng=127.25&radiusKm=10").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.content[0].basecampId").isEqualTo(77);
        assertThat(result).bodyJson().extractingPath("$.content[0].spot.type").isEqualTo("BAKJI");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].joinCondition.sameGenderOnly")
                .isEqualTo(false);
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].canApply");
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].unmetReasons");
    }

    @Test
    @DisplayName("[F-12] 로그인한 검색 응답에는 canApply와 unmetReasons가 있다")
    void loggedInSearchIncludesEligibilityFields() {
        // given
        JoinEligibility unmet =
                new JoinEligibility(false, List.of(JoinUnmetReason.TRUST_LEVEL, JoinUnmetReason.GENDER));
        JoinEligibility ok = new JoinEligibility(true, List.of());
        when(basecampSearchService.search(any(), any()))
                .thenReturn(new BasecampSearchPage(List.of(searchItem(unmet), searchItem(ok)), 0, 20, false));

        // when
        MvcTestResult result = mvc.get()
                .uri(BASECAMPS + "?lat=37.25&lng=127.25&radiusKm=10")
                .with(unverified())
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].canApply").isEqualTo(false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].unmetReasons")
                .isEqualTo(List.of("TRUST_LEVEL", "GENDER"));
        assertThat(result).bodyJson().extractingPath("$.content[1].canApply").isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[1].unmetReasons")
                .isEqualTo(List.of());
    }

    @Test
    @DisplayName("[F-12][NFR-11] 비로그인 상세 응답은 멤버를 닉네임과 역할만 담고 myRelation이 NONE이며, 연락 수단·신뢰 정보 필드가 없다")
    void anonymousDetailShowsOnlyNicknames() {
        // given
        when(basecampDetailQueryService.find(any(), eq(77L))).thenReturn(detail(BasecampRelation.NONE, null));

        // when
        MvcTestResult result = mvc.get().uri(BASECAMPS + "/77").exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(basecampDetailQueryService).find(eq(null), eq(77L));
        assertThat(result).bodyJson().extractingPath("$.myRelation").isEqualTo("NONE");
        assertThat(result).bodyJson().extractingPath("$.description").isEqualTo("함께 가요");
        assertThat(result).bodyJson().extractingPath("$.leader.nickname").isEqualTo("새벽능선");
        assertThat(result).bodyJson().extractingPath("$.members[1].role").isEqualTo("MEMBER");
        assertThat(result).bodyJson().extractingPath("$.safetyNotice").isNotNull();
        assertThat(result).bodyJson().doesNotHavePath("$.leader.trustLevel");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].ageGroup");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].gender");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].trustLevel");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].completedCompanions");
        assertThat(result).bodyJson().doesNotHavePath("$.contactInfo");
    }

    @Test
    @DisplayName("[F-12][NFR-11] 로그인한 상세 응답은 멤버의 프로필 요약을 담고, 연락 수단이 없으면 contactInfo 필드를 뺀다")
    void loggedInDetailShowsProfileAndOmitsMissingContact() {
        // given
        when(basecampDetailQueryService.find(any(), eq(77L))).thenReturn(detail(BasecampRelation.NONE, null));

        // when
        MvcTestResult result =
                mvc.get().uri(BASECAMPS + "/77").with(unverified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        verify(basecampDetailQueryService).find(eq(MEMBER_ID), eq(77L));
        assertThat(result).bodyJson().extractingPath("$.leader.trustLevel").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.members[0].ageGroup").isEqualTo("THIRTIES");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.members[0].ageGroupVerified")
                .isEqualTo(true);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.members[0].completedCompanions")
                .isEqualTo(5);
        assertThat(result).bodyJson().doesNotHavePath("$.contactInfo");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].email");
        assertThat(result).bodyJson().doesNotHavePath("$.members[0].birthYear");
    }

    @Test
    @DisplayName("[F-12] 서비스가 신청 자격을 주면 로그인한 상세 응답에 canApply와 unmetReasons가 있고, 주지 않으면 필드가 없다")
    void loggedInDetailIncludesEligibilityOnlyWhenServiceGivesIt() {
        // given
        when(basecampDetailQueryService.find(any(), eq(77L)))
                .thenReturn(detail(
                        BasecampRelation.NONE, null, new JoinEligibility(false, List.of(JoinUnmetReason.TRUST_LEVEL))));
        when(basecampDetailQueryService.find(any(), eq(78L))).thenReturn(detail(BasecampRelation.NONE, null));

        // when
        MvcTestResult with = mvc.get().uri(BASECAMPS + "/77").with(unverified()).exchange();
        MvcTestResult without =
                mvc.get().uri(BASECAMPS + "/78").with(unverified()).exchange();

        // then
        assertThat(with).bodyJson().extractingPath("$.canApply").isEqualTo(false);
        assertThat(with).bodyJson().extractingPath("$.unmetReasons").isEqualTo(List.of("TRUST_LEVEL"));
        assertThat(without).bodyJson().doesNotHavePath("$.canApply");
        assertThat(without).bodyJson().doesNotHavePath("$.unmetReasons");
    }

    @Test
    @DisplayName("[F-12][BC-23] 서비스가 연락 수단을 주면 로그인한 상세 응답에 contactInfo가 있다")
    void loggedInDetailIncludesContactWhenServiceGivesIt() {
        // given
        when(basecampDetailQueryService.find(any(), eq(77L)))
                .thenReturn(detail(BasecampRelation.MEMBER, "https://open.kakao.com/o/abc"));

        // when
        MvcTestResult result = mvc.get().uri(BASECAMPS + "/77").with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.myRelation").isEqualTo("MEMBER");
        assertThat(result).bodyJson().extractingPath("$.contactInfo").isEqualTo("https://open.kakao.com/o/abc");
    }

    @Test
    @DisplayName("[F-12] 없는 베이스캠프를 조회하면 404 NOT_FOUND이고, 숫자가 아닌 ID는 400 INVALID_INPUT이다")
    void detailNotFoundAndInvalidId() {
        // given
        when(basecampDetailQueryService.find(any(), eq(404L)))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult missing = mvc.get().uri(BASECAMPS + "/404").exchange();
        MvcTestResult notNumber = mvc.get().uri(BASECAMPS + "/abc").exchange();

        // then
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(notNumber).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(notNumber).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("[F-13][BC-06] 합류를 신청하면 201과 applicationId·status를 응답하고, 경로의 베이스캠프 ID와 로그인한 회원 ID, 메시지를 서비스에 넘긴다")
    void applyReturnsCreated() {
        // given
        when(basecampApplyService.apply(any())).thenReturn(new BasecampApplyResult(901L, "PENDING"));

        // when
        MvcTestResult result = postApply(verified(), "{\"message\":\"같이 가고 싶어요\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"applicationId\": 901, \"status\": \"PENDING\" }");
        verify(basecampApplyService).apply(new BasecampApplyCommand(77L, MEMBER_ID, "같이 가고 싶어요"));
    }

    @Test
    @DisplayName("[F-13][BC-06] 요청 본문이 없거나 message가 없어도 201이고, 메시지는 null로 넘긴다")
    void applyAcceptsMissingBodyAndMessage() {
        // given
        when(basecampApplyService.apply(any())).thenReturn(new BasecampApplyResult(901L, "PENDING"));

        // when
        MvcTestResult withoutBody =
                mvc.post().uri(APPLICATIONS).with(verified()).with(csrf()).exchange();
        MvcTestResult withoutMessage = postApply(verified(), "{}");

        // then
        assertThat(withoutBody).hasStatus(HttpStatus.CREATED);
        assertThat(withoutMessage).hasStatus(HttpStatus.CREATED);
        verify(basecampApplyService, times(2)).apply(new BasecampApplyCommand(77L, MEMBER_ID, null));
    }

    @Test
    @DisplayName("[F-13] 신청 메시지가 500자이면 받고, 501자이면 message 필드 오류로 400 INVALID_INPUT을 응답한다")
    void applyMessageLengthBoundary() {
        // given
        when(basecampApplyService.apply(any())).thenReturn(new BasecampApplyResult(901L, "PENDING"));

        // when
        MvcTestResult atLimit = postApply(verified(), "{\"message\":\"" + "가".repeat(500) + "\"}");
        MvcTestResult overLimit = postApply(verified(), "{\"message\":\"" + "가".repeat(501) + "\"}");

        // then
        assertThat(atLimit).hasStatus(HttpStatus.CREATED);
        assertThat(overLimit).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(overLimit).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(overLimit)
                .bodyJson()
                .extractingPath("$.fieldErrors[0].field")
                .isEqualTo("message");
        verify(basecampApplyService).apply(any());
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않은 사용자가 신청하면 401, 이메일 인증 전의 회원이 신청하면 403 MEMBER_NOT_VERIFIED이고 서비스를 부르지 않는다")
    void applyRequiresVerifiedLogin() {
        // when
        MvcTestResult anonymous = mvc.post()
                .uri(APPLICATIONS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();
        MvcTestResult unverifiedMember = postApply(unverified(), "{}");

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(unverifiedMember).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverifiedMember).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampApplyService);
    }

    @Test
    @DisplayName("[F-13] CSRF 토큰 없이 신청하거나 취소하면 403을 응답하고 서비스를 부르지 않는다")
    void applyAndCancelRequireCsrf() {
        // when
        MvcTestResult apply = mvc.post()
                .uri(APPLICATIONS)
                .with(verified())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();
        MvcTestResult cancel = mvc.delete().uri(MY_APPLICATION).with(verified()).exchange();

        // then
        assertThat(apply).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(cancel).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(basecampApplyService, basecampApplicationCancelService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("applyErrors")
    @DisplayName("[F-13] 신청 서비스가 던진 오류는 코드와 상태 그대로 응답한다")
    void applyErrorsAreMapped(BusinessException error, HttpStatus status) {
        // given
        when(basecampApplyService.apply(any())).thenThrow(error);

        // when
        MvcTestResult result = postApply(verified(), "{}");

        // then
        assertThat(result).hasStatus(status);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.code")
                .isEqualTo(error.getErrorCode().name());
    }

    static Stream<Arguments> applyErrors() {
        return Stream.of(
                Arguments.of(new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT), HttpStatus.FORBIDDEN),
                Arguments.of(new BusinessException(CommonErrorCode.NOT_FOUND), HttpStatus.NOT_FOUND),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_INVALID_STATE), HttpStatus.CONFLICT),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_ALREADY_APPLIED), HttpStatus.CONFLICT),
                Arguments.of(
                        new BasecampException(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED), HttpStatus.CONFLICT),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_CONDITION_NOT_MET), HttpStatus.FORBIDDEN),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_DATE_CONFLICT), HttpStatus.CONFLICT),
                Arguments.of(new BasecampException(BasecampErrorCode.BASECAMP_PENDING_LIMIT), HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("[F-13][BC-06] 신청을 취소하면 204이고, 경로의 베이스캠프 ID와 로그인한 회원 ID를 서비스에 넘긴다")
    void cancelApplicationReturnsNoContent() {
        // when
        MvcTestResult result =
                mvc.delete().uri(MY_APPLICATION).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(basecampApplicationCancelService).cancel(77L, MEMBER_ID);
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않은 사용자가 취소하면 401, 이메일 인증 전의 회원이 취소하면 403 MEMBER_NOT_VERIFIED다")
    void cancelRequiresVerifiedLogin() {
        // when
        MvcTestResult anonymous = mvc.delete().uri(MY_APPLICATION).with(csrf()).exchange();
        MvcTestResult unverifiedMember =
                mvc.delete().uri(MY_APPLICATION).with(unverified()).with(csrf()).exchange();

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unverifiedMember).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(unverifiedMember).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampApplicationCancelService);
    }

    @Test
    @DisplayName("[F-13] 취소 서비스가 던진 NOT_FOUND와 BASECAMP_INVALID_STATE는 코드와 상태 그대로 응답한다")
    void cancelErrorsAreMapped() {
        // given
        doThrow(new BusinessException(CommonErrorCode.NOT_FOUND))
                .doThrow(new BasecampException(BasecampErrorCode.BASECAMP_INVALID_STATE))
                .when(basecampApplicationCancelService)
                .cancel(77L, MEMBER_ID);

        // when
        MvcTestResult notFound =
                mvc.delete().uri(MY_APPLICATION).with(verified()).with(csrf()).exchange();
        MvcTestResult invalidState =
                mvc.delete().uri(MY_APPLICATION).with(verified()).with(csrf()).exchange();

        // then
        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(invalidState).hasStatus(HttpStatus.CONFLICT);
        assertThat(invalidState).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_INVALID_STATE");
    }

    @Test
    @DisplayName("[F-13][BC-08] 승인하면 200이고 인원과 베이스캠프 상태를 응답하며, 경로의 ID와 로그인한 회원 ID를 서비스에 넘긴다")
    void approveReturnsHeadcountAndStatus() {
        // given
        when(basecampApprovalService.approve(77L, 501L, MEMBER_ID)).thenReturn(new BasecampApproveResult(4, "CLOSED"));

        // when
        MvcTestResult result =
                mvc.post().uri(APPROVE).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"headcount\": 4, \"status\": \"CLOSED\" }");
    }

    @Test
    @DisplayName("[F-13][BC-08] 거절하면 200이고 applicationId와 REJECTED를 응답한다")
    void rejectReturnsApplicationStatus() {
        // given
        when(basecampApprovalService.reject(77L, 501L, MEMBER_ID))
                .thenReturn(new BasecampRejectResult(501L, "REJECTED"));

        // when
        MvcTestResult result =
                mvc.post().uri(REJECT).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"applicationId\": 501, \"status\": \"REJECTED\" }");
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않은 사용자가 승인·거절·신청 목록을 부르면 401이고, 이메일 인증 전의 회원은 403 MEMBER_NOT_VERIFIED다")
    void decisionEndpointsRequireVerifiedLogin() {
        // when
        MvcTestResult approveAnonymous = mvc.post().uri(APPROVE).with(csrf()).exchange();
        MvcTestResult rejectAnonymous = mvc.post().uri(REJECT).with(csrf()).exchange();
        MvcTestResult listAnonymous = mvc.get().uri(APPLICATIONS).exchange();
        MvcTestResult approveUnverified =
                mvc.post().uri(APPROVE).with(unverified()).with(csrf()).exchange();
        MvcTestResult listUnverified =
                mvc.get().uri(APPLICATIONS).with(unverified()).exchange();

        // then
        assertThat(approveAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(rejectAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(listAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(approveUnverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(listUnverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(listUnverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampApprovalService, basecampApplicationListService);
    }

    @Test
    @DisplayName("[F-13] 승인·거절 서비스가 던진 ACCESS_DENIED, NOT_FOUND, BASECAMP_FULL은 코드와 상태 그대로 응답한다")
    void decisionErrorsAreMapped() {
        // given
        when(basecampApprovalService.approve(77L, 501L, MEMBER_ID))
                .thenThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .thenThrow(new BasecampException(BasecampErrorCode.BASECAMP_FULL));
        when(basecampApprovalService.reject(77L, 501L, MEMBER_ID))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when
        MvcTestResult denied =
                mvc.post().uri(APPROVE).with(verified()).with(csrf()).exchange();
        MvcTestResult full =
                mvc.post().uri(APPROVE).with(verified()).with(csrf()).exchange();
        MvcTestResult notFound =
                mvc.post().uri(REJECT).with(verified()).with(csrf()).exchange();

        // then
        assertThat(denied).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(full).hasStatus(HttpStatus.CONFLICT);
        assertThat(full).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_FULL");
        assertThat(notFound).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(notFound).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("[F-13] 신청 목록의 status를 생략하면 PENDING, page는 0, size는 20으로 서비스를 부른다")
    void listApplicationsUsesDefaults() {
        // given
        when(basecampApplicationListService.list(eq(77L), eq(MEMBER_ID), any()))
                .thenReturn(new BasecampApplicationPage(List.of(), 0, 20, false));

        // when
        MvcTestResult result = mvc.get().uri(APPLICATIONS).with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyJson()
                .isStrictlyEqualTo("{ \"content\": [], \"page\": 0, \"size\": 20, \"hasNext\": false }");
        verify(basecampApplicationListService)
                .list(77L, MEMBER_ID, new BasecampApplicationListQuery(BasecampApplicationStatus.PENDING, 0, 20));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"status=UNKNOWN", "status=pending", "size=0", "size=51", "page=-1"})
    @DisplayName("[F-13] 신청 목록의 status가 허용 값이 아니거나 page·size가 범위를 벗어나면 400 INVALID_INPUT이다")
    void listApplicationsRejectsInvalidParameters(String query) {
        // when
        MvcTestResult result =
                mvc.get().uri(APPLICATIONS + "?" + query).with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        verifyNoInteractions(basecampApplicationListService);
    }

    @Test
    @DisplayName("[F-13][BC-21] 탈퇴하면 204이고 경로의 베이스캠프 ID와 로그인한 회원 ID를 서비스에 넘긴다")
    void leaveReturnsNoContent() {
        // when
        MvcTestResult result =
                mvc.delete().uri(LEAVE).with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(basecampMembershipService).leave(77L, MEMBER_ID);
    }

    @Test
    @DisplayName("[F-13][BC-22] 강퇴하면 204이고 경로의 ID, 로그인한 회원 ID, 사유를 서비스에 넘긴다")
    void kickReturnsNoContent() {
        // when
        MvcTestResult result = postKick(verified(), "{\"reason\":\"NO_CONTACT\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(basecampMembershipService).kick(77L, 31L, MEMBER_ID, KickReason.NO_CONTACT);
    }

    @Test
    @DisplayName("[F-13] 로그인하지 않은 사용자는 탈퇴·강퇴가 401이고, 이메일 인증 전의 회원은 403 MEMBER_NOT_VERIFIED다")
    void membershipEndpointsRequireVerifiedLogin() {
        // when
        MvcTestResult leaveAnonymous = mvc.delete().uri(LEAVE).with(csrf()).exchange();
        MvcTestResult kickAnonymous = mvc.post()
                .uri(KICK)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"OTHER\"}")
                .exchange();
        MvcTestResult leaveUnverified =
                mvc.delete().uri(LEAVE).with(unverified()).with(csrf()).exchange();
        MvcTestResult kickUnverified = postKick(unverified(), "{\"reason\":\"OTHER\"}");

        // then
        assertThat(leaveAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(kickAnonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(leaveUnverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(kickUnverified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(kickUnverified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        verifyNoInteractions(basecampMembershipService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"UNKNOWN\"}", "{\"reason\":\"other\"}"})
    @DisplayName("[F-13][BC-22] 강퇴 사유가 없거나 정해진 값이 아니면 400 INVALID_INPUT이고 fieldErrors가 있다")
    void kickRejectsMissingOrUnknownReason(String body) {
        // when
        MvcTestResult result = postKick(verified(), body);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors").isNotNull();
        verifyNoInteractions(basecampMembershipService);
    }

    @Test
    @DisplayName("[F-13] 탈퇴·강퇴 서비스가 던진 오류는 코드와 상태 그대로 응답한다")
    void membershipErrorsAreMapped() {
        // given
        doThrow(new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT))
                .doThrow(new BasecampException(BasecampErrorCode.BASECAMP_LEADER_CANNOT_LEAVE))
                .when(basecampMembershipService)
                .leave(77L, MEMBER_ID);
        doThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .doThrow(new BasecampException(BasecampErrorCode.BASECAMP_INVALID_STATE))
                .when(basecampMembershipService)
                .kick(77L, 31L, MEMBER_ID, KickReason.OTHER);

        // when
        MvcTestResult lowTrust =
                mvc.delete().uri(LEAVE).with(verified()).with(csrf()).exchange();
        MvcTestResult leader =
                mvc.delete().uri(LEAVE).with(verified()).with(csrf()).exchange();
        MvcTestResult denied = postKick(verified(), "{\"reason\":\"OTHER\"}");
        MvcTestResult invalidState = postKick(verified(), "{\"reason\":\"OTHER\"}");

        // then
        assertThat(lowTrust).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(lowTrust).bodyJson().extractingPath("$.code").isEqualTo("TRUST_LEVEL_INSUFFICIENT");
        assertThat(leader).hasStatus(HttpStatus.CONFLICT);
        assertThat(leader).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_LEADER_CANNOT_LEAVE");
        assertThat(denied).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(denied).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(invalidState).hasStatus(HttpStatus.CONFLICT);
        assertThat(invalidState).bodyJson().extractingPath("$.code").isEqualTo("BASECAMP_INVALID_STATE");
    }

    private MvcTestResult postKick(RequestPostProcessor login, String requestBody) {
        return mvc.post()
                .uri(KICK)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();
    }

    private static BasecampSearchItem searchItem(JoinEligibility eligibility) {
        return new BasecampSearchItem(
                77L,
                "굴업도 주말 1박",
                new BasecampSearchItem.SpotSummary(101L, "개머리언덕", "BAKJI", 37.25, 127.25),
                LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 8),
                4,
                3,
                "RECRUITING",
                new JoinConditionSummary(null, null, null, false),
                eligibility);
    }

    private static BasecampDetail detail(BasecampRelation relation, String contactInfo) {
        return detail(relation, contactInfo, null);
    }

    private static BasecampDetail detail(BasecampRelation relation, String contactInfo, JoinEligibility eligibility) {
        return new BasecampDetail(
                77L,
                "굴업도 주말 1박",
                "함께 가요",
                new BasecampDetail.SpotSummary(101L, "개머리언덕", "BAKJI"),
                LocalDate.of(2026, 11, 7),
                LocalDate.of(2026, 11, 8),
                4,
                2,
                "CONFIRMED",
                new JoinConditionSummary(1, null, null, false),
                new BasecampDetail.DetailMember(31L, "새벽능선", "LEADER", "THIRTIES", true, "FEMALE", true, 2, 5),
                List.of(
                        new BasecampDetail.DetailMember(31L, "새벽능선", "LEADER", "THIRTIES", true, "FEMALE", true, 2, 5),
                        new BasecampDetail.DetailMember(32L, "달빛야영", "MEMBER", null, false, null, false, 1, 0)),
                relation,
                contactInfo,
                eligibility);
    }

    private static String dates() {
        return "\"startDate\":\"2026-10-20\",\"endDate\":\"2026-10-22\"";
    }

    private static String body(String... overrides) {
        return "{\"spotId\":101,\"description\":\"함께 가요\"," + dates() + "," + String.join(",", overrides) + "}";
    }

    private MvcTestResult postApply(RequestPostProcessor login, String requestBody) {
        return mvc.post()
                .uri(APPLICATIONS)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .exchange();
    }

    private MvcTestResult post(RequestPostProcessor login, String requestBody) {
        return mvc.post()
                .uri(BASECAMPS)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
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
