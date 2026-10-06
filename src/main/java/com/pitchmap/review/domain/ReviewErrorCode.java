package com.pitchmap.review.domain;

import com.pitchmap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReviewErrorCode implements ErrorCode {
    SPOT_REVIEW_DUPLICATED(HttpStatus.CONFLICT, "같은 장소에 같은 방문일로 쓴 후기가 이미 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ReviewErrorCode(HttpStatus httpStatus, String message) {
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
