package com.pitchmap.basecamp.domain;

import com.pitchmap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BasecampErrorCode implements ErrorCode {
    BASECAMP_CAPACITY_INVALID(HttpStatus.BAD_REQUEST, "정원은 캠프 리더를 포함해 2~6명이어야 하고, 열린 뒤에는 줄일 수 없습니다."),
    BASECAMP_REAPPLY_NOT_ALLOWED(HttpStatus.CONFLICT, "거절·탈퇴·강퇴된 베이스캠프에는 다시 신청할 수 없습니다."),
    BASECAMP_ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 신청 중이거나 멤버인 베이스캠프입니다."),
    BASECAMP_FULL(HttpStatus.CONFLICT, "베이스캠프 정원이 가득 찼습니다."),
    BASECAMP_INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 할 수 없는 동작입니다."),
    BASECAMP_NOT_ENOUGH_MEMBERS(HttpStatus.CONFLICT, "인원이 2명 미만이면 확정할 수 없습니다."),
    BASECAMP_LEADER_CANNOT_LEAVE(HttpStatus.CONFLICT, "캠프 리더는 탈퇴하거나 강퇴될 수 없습니다. 베이스캠프를 취소해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    BasecampErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public String message() {
        return message;
    }
}
