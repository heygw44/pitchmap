package com.pitchmap.member.domain;

import com.pitchmap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MemberErrorCode implements ErrorCode {
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    MEMBER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    MEMBER_DISPOSABLE_EMAIL(HttpStatus.BAD_REQUEST, "일회용 이메일로는 가입할 수 없습니다."),
    MEMBER_PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "비밀번호는 10~64자이고 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    MEMBER_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정입니다."),
    EMAIL_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다. 코드를 다시 받아 주세요."),
    EMAIL_CODE_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "인증 코드를 5번 틀렸습니다. 코드를 다시 받아 주세요."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "이미 이메일 인증을 마쳤습니다."),
    EMAIL_RESEND_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "인증 코드를 너무 자주 요청했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    MemberErrorCode(HttpStatus httpStatus, String message) {
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
