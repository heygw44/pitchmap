package com.pitchmap.trust.domain;

import com.pitchmap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TrustErrorCode implements ErrorCode {
    IDENTITY_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 본인확인을 마친 계정입니다."),
    IDENTITY_CI_DUPLICATED(HttpStatus.CONFLICT, "이미 본인확인된 계정이 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    TrustErrorCode(HttpStatus httpStatus, String message) {
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
