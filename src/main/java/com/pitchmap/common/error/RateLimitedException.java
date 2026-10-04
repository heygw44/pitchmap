package com.pitchmap.common.error;

import java.time.Duration;

public class RateLimitedException extends BusinessException {

    private final Duration retryAfter;

    public RateLimitedException(ErrorCode errorCode, Duration retryAfter) {
        super(errorCode);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
