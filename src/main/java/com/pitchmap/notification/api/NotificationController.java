package com.pitchmap.notification.api;

import com.pitchmap.common.security.LoginMember;
import com.pitchmap.notification.application.NotificationQueryService;
import com.pitchmap.notification.application.NotificationReadService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationReadService notificationReadService;

    @Operation(
            summary = "알림함 목록",
            description = "로그인한 회원의 알림을 최신순으로 돌려준다. page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                    + "응답에는 전체 개수가 없고 다음 페이지가 있는지만 hasNext로 알려 준다. "
                    + "content의 각 항목에는 notificationId, type, title, body, link, readAt, createdAt이 있다. "
                    + "link는 연결할 화면이 없으면 null이고, readAt은 아직 읽지 않았으면 null이다. "
                    + "로그인하지 않았으면 401 AUTHENTICATION_REQUIRED, page·size가 범위를 벗어나면 400 INVALID_INPUT이다.")
    @GetMapping
    NotificationPageResponse list(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @ParameterObject @ModelAttribute NotificationListRequest request) {
        return NotificationPageResponse.from(notificationQueryService.list(
                loginMember.memberId(), request.pageOrDefault(), request.sizeOrDefault()));
    }

    @Operation(
            summary = "안 읽은 알림 수",
            description = "로그인한 회원의 안 읽은 알림 수를 count로 돌려준다. 로그인하지 않았으면 401 AUTHENTICATION_REQUIRED이다.")
    @GetMapping("/unread-count")
    UnreadCountResponse countUnread(@AuthenticationPrincipal LoginMember loginMember) {
        return new UnreadCountResponse(notificationQueryService.countUnread(loginMember.memberId()));
    }

    @Operation(
            summary = "알림 읽음 처리",
            description = "로그인한 회원의 알림 한 건을 읽음으로 바꾸고 204로 응답한다. 이미 읽은 알림이면 처음 읽은 시각을 그대로 두고 204로 응답한다. "
                    + "알림이 없거나 다른 회원의 알림이면 존재를 드러내지 않으려고 404 NOT_FOUND로 응답한다.")
    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@AuthenticationPrincipal LoginMember loginMember, @PathVariable long notificationId) {
        notificationReadService.markRead(loginMember.memberId(), notificationId);
    }

    @Operation(summary = "알림 모두 읽음 처리", description = "로그인한 회원의 안 읽은 알림을 모두 읽음으로 바꾸고 204로 응답한다. 안 읽은 알림이 없어도 204이다.")
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(@AuthenticationPrincipal LoginMember loginMember) {
        notificationReadService.markAllRead(loginMember.memberId());
    }
}
