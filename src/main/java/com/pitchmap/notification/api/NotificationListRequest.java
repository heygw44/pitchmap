package com.pitchmap.notification.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record NotificationListRequest(
        @Schema(description = "페이지 번호. 0부터 시작하고 생략하면 0이다.") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        Integer page,

        @Schema(description = "페이지 크기. 1~50이고 생략하면 20이다.")
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }
}
