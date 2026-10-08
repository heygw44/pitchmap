package com.pitchmap.program.domain;

import com.pitchmap.common.error.BusinessException;

public class ProgramException extends BusinessException {

    public ProgramException(ProgramErrorCode errorCode) {
        super(errorCode);
    }
}
