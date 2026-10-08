package com.pitchmap.basecamp.domain;

import com.pitchmap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BasecampErrorCode implements ErrorCode {
    BASECAMP_CAPACITY_INVALID(HttpStatus.BAD_REQUEST, "정원은 캠프 리더를 포함해 2~6명이어야 하고, 열린 뒤에는 줄일 수 없습니다."),
    BASECAMP_OPEN_LIMIT(HttpStatus.BAD_REQUEST, "모집 중이거나 마감된 베이스캠프는 최대 3개까지만 열 수 있습니다."),
    BASECAMP_SCHEDULE_INVALID(HttpStatus.BAD_REQUEST, "출발일은 내일부터 60일 안이어야 하고, 1~3박이어야 합니다."),
    BASECAMP_WARNING_SPOT(HttpStatus.BAD_REQUEST, "공원 경계 경고가 있는 박지에서는 베이스캠프를 열 수 없습니다."),
    BASECAMP_CONDITION_NOT_MET(HttpStatus.FORBIDDEN, "캠프 리더가 건 합류 조건을 충족하지 못했습니다."),
    BASECAMP_DATE_CONFLICT(HttpStatus.CONFLICT, "같은 기간에 확정된 다른 베이스캠프의 멤버입니다."),
    BASECAMP_PENDING_LIMIT(HttpStatus.CONFLICT, "이 베이스캠프에 결정을 기다리는 신청이 가득 찼습니다."),
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
