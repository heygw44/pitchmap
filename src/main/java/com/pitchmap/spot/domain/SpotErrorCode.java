package com.pitchmap.spot.domain;

import com.pitchmap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SpotErrorCode implements ErrorCode {
    SPOT_RADIUS_TOO_LARGE(HttpStatus.BAD_REQUEST, "반경은 50km 이하여야 합니다."),
    BAKJI_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 확인한 박지입니다."),
    BAKJI_ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 박지입니다."),
    SPOT_INVALID_STATE(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없는 조치입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    SpotErrorCode(HttpStatus httpStatus, String message) {
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
