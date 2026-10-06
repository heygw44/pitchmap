package com.pitchmap.spot.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.pitchmap.spot.application.BakjiCommandService;
import com.pitchmap.spot.application.BakjiDuplicateCandidate;
import com.pitchmap.spot.application.BakjiReportCommand;
import com.pitchmap.spot.application.BakjiSubmission;
import com.pitchmap.spot.application.BakjiUpdateCommand;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(BakjiController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class, SecurityConfig.class})
class BakjiControllerTest {

    private static final String PATH = "/api/bakjis";
    private static final long MEMBER_ID = 7L;
    private static final String VALID_BODY =
            "{\"name\":\"능선 끝 평지\",\"lat\":37.6,\"lng\":128.7,\"hasWater\":true,\"hasToilet\":false}";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BakjiCommandService bakjiCommandService;

    @Test
    @DisplayName("[F-07] 경고 박지를 제보하면 201과 함께 경고, 공원 이름, 안내 문구, 중복 후보를 응답한다")
    void warnedReportReturnsCreatedWithAreaName() {
        // given
        when(bakjiCommandService.report(eq(MEMBER_ID), any()))
                .thenReturn(new BakjiSubmission(
                        205L, true, "설악산국립공원", "경고 문구", List.of(new BakjiDuplicateCandidate(101L, "능선 끝 평지", 32))));

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "spotId": 205,
                  "parkWarning": { "warned": true, "areaName": "설악산국립공원" },
                  "guide": "경고 문구",
                  "duplicateCandidates": [{ "spotId": 101, "name": "능선 끝 평지", "distanceM": 32 }]
                }
                """);
    }

    @Test
    @DisplayName("[F-07] 경고가 아닌 박지를 제보하면 parkWarning에 warned만 있고 areaName 필드는 없다")
    void notWarnedReportOmitsAreaName() {
        // given
        when(bakjiCommandService.report(eq(MEMBER_ID), any()))
                .thenReturn(new BakjiSubmission(205L, false, null, "일반 문구", List.of()));

        // when
        MvcTestResult result = post(verified(), VALID_BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {
                  "spotId": 205,
                  "parkWarning": { "warned": false },
                  "guide": "일반 문구",
                  "duplicateCandidates": []
                }
                """);
    }

    @Test
    @DisplayName("[F-07] 요청 값을 서비스 명령으로 그대로 넘기고, 제보한 회원의 ID를 쓴다")
    void reportPassesRequestToService() {
        // given
        when(bakjiCommandService.report(anyLong(), any()))
                .thenReturn(new BakjiSubmission(205L, false, null, "g", List.of()));

        // when
        post(
                verified(),
                "{\"name\":\"이름\",\"lat\":37.6,\"lng\":128.7,\"description\":\"설명\","
                        + "\"hasWater\":true,\"hasToilet\":true,\"signalLevel\":\"GOOD\",\"groundType\":\"ROCK\"}");

        // then
        ArgumentCaptor<BakjiReportCommand> captor = ArgumentCaptor.forClass(BakjiReportCommand.class);
        verify(bakjiCommandService).report(eq(MEMBER_ID), captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new BakjiReportCommand("이름", 37.6, 128.7, "설명", true, true, "GOOD", "ROCK"));
    }

    @Test
    @DisplayName("[F-07] 이름이 비었거나 물·화장실 유무가 빠졌거나 위도가 범위를 벗어나면 400 INVALID_INPUT과 필드 오류를 응답한다")
    void invalidBodyReturnsFieldErrors() {
        // when
        MvcTestResult result = post(verified(), "{\"name\":\" \",\"lat\":91,\"lng\":128.7,\"hasWater\":true}");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='name')]")
                .asList()
                .isNotEmpty();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='lat')]")
                .asList()
                .isNotEmpty();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='hasToilet')]")
                .asList()
                .isNotEmpty();
        verifyNoInteractions(bakjiCommandService);
    }

    @Test
    @DisplayName("[F-07] 기상청 격자 밖 좌표로 제보하면 400 INVALID_INPUT이고 lat와 lng에 필드 오류가 하나씩 붙는다")
    void outsideWeatherGridReturnsFieldErrorsOnLatAndLng() {
        // when
        MvcTestResult result = post(
                verified(), "{\"name\":\"도쿄\",\"lat\":35.6762,\"lng\":139.6503,\"hasWater\":true,\"hasToilet\":true}");

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors").asList().hasSize(2);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='lat')].reason")
                .asList()
                .containsExactly("서비스 지역 밖 좌표입니다.");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.fieldErrors[?(@.field=='lng')].reason")
                .asList()
                .containsExactly("서비스 지역 밖 좌표입니다.");
        verifyNoInteractions(bakjiCommandService);
    }

    @Test
    @DisplayName("[F-07] 로그인하지 않은 사용자가 제보하면 401 AUTHENTICATION_REQUIRED를 응답한다")
    void anonymousReportIsUnauthorized() {
        // when
        MvcTestResult result = mvc.post()
                .uri(PATH)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)
                .exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @DisplayName("[F-07] 수정 요청은 보낸 필드와 null로 보낸 필드를 구별해서 서비스에 넘기고, 200과 제보와 같은 본문을 응답한다")
    void updateDistinguishesAbsentAndNullFields() {
        // given
        when(bakjiCommandService.update(eq(MEMBER_ID), eq(205L), any()))
                .thenReturn(new BakjiSubmission(205L, false, null, "일반 문구", List.of()));

        // when
        MvcTestResult result = patch(verified(), 205L, "{\"name\":\"새 이름\",\"description\":null}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.spotId").isEqualTo(205);
        assertThat(result).bodyJson().extractingPath("$.parkWarning.warned").isEqualTo(false);
        ArgumentCaptor<BakjiUpdateCommand> captor = ArgumentCaptor.forClass(BakjiUpdateCommand.class);
        verify(bakjiCommandService).update(eq(MEMBER_ID), eq(205L), captor.capture());
        BakjiUpdateCommand command = captor.getValue();
        assertThat(command.name().present()).isTrue();
        assertThat(command.name().value()).isEqualTo("새 이름");
        assertThat(command.description().present()).isTrue();
        assertThat(command.description().value()).isNull();
        assertThat(command.lat().present()).isFalse();
        assertThat(command.hasWater().present()).isFalse();
    }

    @Test
    @DisplayName("[F-07] 서비스가 ACCESS_DENIED로 거부하면 수정은 403을 응답한다")
    void updateByOtherMemberIsForbidden() {
        // given
        when(bakjiCommandService.update(anyLong(), anyLong(), any()))
                .thenThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));

        // when
        MvcTestResult result = patch(verified(), 205L, "{\"name\":\"새 이름\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("[F-07] 삭제하면 204를 응답하고 제보한 회원의 ID와 장소 ID로 서비스를 부른다")
    void deleteReturnsNoContent() {
        // when
        MvcTestResult result =
                mvc.delete().uri(PATH + "/205").with(verified()).with(csrf()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(bakjiCommandService).delete(MEMBER_ID, 205L);
    }

    @Test
    @DisplayName("[F-07] 삭제 요청에 CSRF 토큰이 없으면 403을 응답하고 서비스를 부르지 않는다")
    void deleteWithoutCsrfIsForbidden() {
        // when
        MvcTestResult result = mvc.delete().uri(PATH + "/205").with(verified()).exchange();

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(bakjiCommandService);
    }

    private MvcTestResult post(RequestPostProcessor login, String body) {
        return mvc.post()
                .uri(PATH)
                .with(login)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult patch(RequestPostProcessor login, long spotId, String body) {
        return mvc.patch()
                .uri(PATH + "/" + spotId)
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
}
