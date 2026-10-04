package com.pitchmap.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.common.trace.TraceIdFilter;
import java.util.List;
import org.slf4j.MDC;

public record ErrorResponse(
        String code,
        String message,
        String traceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldError> fieldErrors) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.message());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, currentTraceId(), null);
    }

    public static ErrorResponse ofInvalidInput(List<FieldError> fieldErrors) {
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
        return new ErrorResponse(errorCode.name(), errorCode.message(), currentTraceId(), fieldErrors);
    }

    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }

    public record FieldError(String field, String reason) {}
}
