package com.pitchmap.member.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** 비밀번호와 그 확인 값을 함께 받는 요청이 {@link PasswordsMatch} 검사를 받으려고 구현하는 인터페이스. */
interface ConfirmedPassword {

    @JsonIgnore
    String passwordToConfirm();

    @JsonIgnore
    String passwordConfirmation();

    /** 두 값이 다를 때 오류를 붙일 요청 필드 이름. */
    @JsonIgnore
    String confirmationFieldName();
}
