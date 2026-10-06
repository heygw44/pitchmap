package com.pitchmap.common.error;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.common.trace.TraceIdFilter;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

public record ErrorResponse(
        String code,
        String message,
        String traceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldError> fieldErrors,
        @JsonIgnore Map<String, Object> extraFields) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.message());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode, message, Map.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, Map<String, Object> extraFields) {
        return new ErrorResponse(errorCode.name(), message, currentTraceId(), null, extraFields);
    }

    public static ErrorResponse ofInvalidInput(List<FieldError> fieldErrors) {
        ErrorCode errorCode = CommonErrorCode.INVALID_INPUT;
        return new ErrorResponse(errorCode.name(), errorCode.message(), currentTraceId(), fieldErrors, Map.of());
    }

    // 도메인 예외가 알려 주는 추가 값(예: 정지 해제 시각)을 응답 JSON 최상위에 펼쳐 쓴다.
    // 추가 값이 없으면 다른 응답과 JSON 모양이 똑같다.
    @JsonAnyGetter
    public Map<String, Object> additionalFields() {
        return extraFields;
    }

    private static String currentTraceId() {
        return MDC.get(TraceIdFilter.MDC_KEY);
    }

    public record FieldError(String field, String reason) {}
}
