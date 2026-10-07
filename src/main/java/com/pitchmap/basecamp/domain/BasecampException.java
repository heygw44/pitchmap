package com.pitchmap.basecamp.domain;

import com.pitchmap.common.error.BusinessException;

public class BasecampException extends BusinessException {

    public BasecampException(BasecampErrorCode errorCode) {
        super(errorCode);
    }
}
