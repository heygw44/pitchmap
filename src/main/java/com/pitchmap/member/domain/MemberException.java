package com.pitchmap.member.domain;

import com.pitchmap.common.error.BusinessException;

public class MemberException extends BusinessException {

    public MemberException(MemberErrorCode errorCode) {
        super(errorCode);
    }
}
