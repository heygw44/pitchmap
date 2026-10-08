package com.pitchmap.notification.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.notification.application.NotificationSettingItem;
import com.pitchmap.notification.application.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/notification-settings")
@RequiredArgsConstructor
class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @Operation(
            summary = "알림 이메일 설정 조회",
            description = "로그인한 회원의 알림 종류별 이메일 수신 여부를 정해진 순서로 모두 돌려준다. 각 항목은 type과 emailEnabled이다. "
                    + "설정을 저장하지 않은 종류는 emailEnabled가 true이다. 로그인하지 않았으면 401 AUTHENTICATION_REQUIRED이다.")
    @GetMapping
    List<NotificationSettingResponse> list(@AuthenticationPrincipal LoginMember loginMember) {
        return toResponses(notificationSettingService.list(loginMember.memberId()));
    }

    @Operation(
            summary = "알림 이메일 설정 변경",
            description = "로그인한 회원의 알림 이메일 설정을 요청 본문으로 통째로 바꾸고, 바뀐 뒤의 모든 종류별 설정을 조회와 같은 형식으로 돌려준다. "
                    + "본문에 없는 종류는 기본값(emailEnabled true)으로 돌아간다. "
                    + "type이 비었거나 emailEnabled가 없거나 항목이 종류 수보다 많거나, 알 수 없는 type이거나 같은 type이 두 번 있으면 400 INVALID_INPUT이다. "
                    + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED이다.")
    @PutMapping
    List<NotificationSettingResponse> replace(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestBody @Size(max = NotificationSettingService.TYPE_COUNT)
                    List<@NotNull @Valid NotificationSettingRequest> requests) {
        List<NotificationSettingItem> items =
                requests.stream().map(NotificationSettingRequest::toItem).toList();
        return toResponses(notificationSettingService.replace(loginMember.memberId(), items));
    }

    private static List<NotificationSettingResponse> toResponses(List<NotificationSettingItem> items) {
        return items.stream().map(NotificationSettingResponse::from).toList();
    }
}
