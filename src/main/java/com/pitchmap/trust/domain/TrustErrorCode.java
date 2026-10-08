package com.pitchmap.trust.domain;

import com.pitchmap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TrustErrorCode implements ErrorCode {
    IDENTITY_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 본인확인을 마친 계정입니다."),
    IDENTITY_CI_DUPLICATED(HttpStatus.CONFLICT, "이미 본인확인된 계정이 있습니다."),
    COMPANION_REVIEW_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "이 회원에게 동행 후기를 쓸 자격이 없습니다."),
    COMPANION_REVIEW_DEADLINE_PASSED(HttpStatus.BAD_REQUEST, "동행 후기를 쓸 수 있는 기한이 지났습니다."),
    COMPANION_REVIEW_DUPLICATED(HttpStatus.CONFLICT, "같은 베이스캠프에서 이 회원에게 쓴 후기가 이미 있습니다."),
    REPORT_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "이 회원이나 후기를 신고할 자격이 없습니다."),
    REPORT_DUPLICATED(HttpStatus.CONFLICT, "같은 베이스캠프에서 같은 회원을 같은 종류로 이미 신고했습니다.");

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
