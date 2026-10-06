package com.pitchmap.common.error;

import java.util.Map;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // 하위 예외가 재정의하면 응답 JSON 최상위에 이 값이 덧붙는다. 비밀값이나 개인정보는 담지 않는다.
    public Map<String, Object> extraFields() {
        return Map.of();
    }
}
