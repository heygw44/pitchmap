package com.pitchmap.member.domain;

import com.pitchmap.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MemberErrorCode implements ErrorCode {
    MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    MEMBER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    MEMBER_DISPOSABLE_EMAIL(HttpStatus.BAD_REQUEST, "일회용 이메일로는 가입할 수 없습니다."),
    MEMBER_PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "비밀번호는 10~64자이고 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다.");

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
