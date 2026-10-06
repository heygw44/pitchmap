package com.pitchmap.trust.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.member.application.MemberProfile;
import com.pitchmap.member.application.MemberProfileService;
import com.pitchmap.trust.application.MemberProfileQueryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// 프로필에는 회원 모듈의 닉네임·자기 신고 값과 신뢰 모듈의 신뢰 정보가 함께 들어간다.
// 신뢰 모듈이 회원 모듈의 공개 서비스를 부르는 방향만 허용되므로, 두 정보를 모으는 컨트롤러를 신뢰 모듈에 둔다.
@RestController
class MemberProfileController {

    private final MemberProfileService memberProfileService;
    private final MemberProfileQueryService memberProfileQueryService;

    MemberProfileController(
            MemberProfileService memberProfileService, MemberProfileQueryService memberProfileQueryService) {
        this.memberProfileService = memberProfileService;
        this.memberProfileQueryService = memberProfileQueryService;
    }

    @Operation(
            summary = "회원 프로필 조회",
            description = "로그인하지 않고 부르면 memberId와 nickname만 응답한다. "
                    + "로그인한 회원(이메일 인증 전, 본인, 관리자 포함)이 부르면 연령대·성별(각각 본인확인한 값인지 verified로 구분), "
                    + "신뢰 단계, 완료한 동행 횟수, 동행 후기 요약(\"다시 동행\" 비율과 많이 나온 태그)도 응답한다. "
                    + "본인확인한 값이 있으면 그 값을, 없으면 회원이 직접 밝힌 값(밝히지 않았으면 null)을 쓴다. "
                    + "이메일, 출생연도, 본인확인 해시와 연락 수단은 어떤 경우에도 응답하지 않는다. "
                    + "없는 회원이거나 탈퇴한 회원이면 404 NOT_FOUND로 응답한다.")
    @GetMapping("/api/members/{memberId}/profile")
    MemberProfileResponse find(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long memberId) {
        if (loginMember == null) {
            MemberProfile profile = memberProfileService.find(memberId);
            return new MemberProfileResponse.Anonymous(profile.memberId(), profile.nickname());
        }
        return MemberProfileResponse.Member.from(memberProfileQueryService.find(memberId));
    }
}
