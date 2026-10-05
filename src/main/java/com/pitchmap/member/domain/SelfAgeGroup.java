package com.pitchmap.member.domain;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import java.util.Arrays;
import java.util.stream.Collectors;

/** 회원이 스스로 밝힌 연령대다. 본인확인을 거치지 않은 값이라 화면에 보여 주는 데만 쓴다. */
public enum SelfAgeGroup {
    TWENTIES,
    THIRTIES,
    FORTIES,
    FIFTIES,
    SIXTIES_PLUS;

    private static final String ALLOWED_NAMES =
            Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));

    /** 호출하면 이름이 정확히 같은 값을 돌려준다. 허용 목록에 없는 문자열이나 null이면 입력 오류 예외를 던진다. */
    public static SelfAgeGroup parse(String name) {
        for (SelfAgeGroup value : values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        throw new BusinessException(CommonErrorCode.INVALID_INPUT, "연령대는 %s 중 하나여야 합니다.".formatted(ALLOWED_NAMES));
    }
}
