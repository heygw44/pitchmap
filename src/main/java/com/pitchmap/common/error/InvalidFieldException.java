package com.pitchmap.common.error;

import lombok.Getter;

/**
 * 요청 본문의 필드 하나가 규칙을 어겼을 때 던진다. 응답은 INVALID_INPUT(400)이고, 어긴 필드 이름과 이유를 {@code fieldErrors}에 담는다.
 * Bean Validation이 검사할 수 없어서 서비스가 판단하는 입력 오류에 쓴다.
 */
@Getter
public class InvalidFieldException extends BusinessException {

    private final String field;

    public InvalidFieldException(String field, String reason) {
        super(CommonErrorCode.INVALID_INPUT, reason);
        this.field = field;
    }
}
