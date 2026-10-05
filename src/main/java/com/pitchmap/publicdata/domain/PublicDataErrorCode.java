package com.pitchmap.publicdata.domain;

import com.pitchmap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PublicDataErrorCode implements ErrorCode {
    SYNC_JOB_ALREADY_RUNNING(HttpStatus.CONFLICT, "같은 종류의 작업이 이미 실행 중입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    PublicDataErrorCode(HttpStatus httpStatus, String message) {
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
