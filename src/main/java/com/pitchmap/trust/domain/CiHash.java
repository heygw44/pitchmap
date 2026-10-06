package com.pitchmap.trust.domain;

import java.util.regex.Pattern;

/**
 * 본인확인기관의 고유 식별값(CI)을 비밀 키로 해시한 값이다. 소문자 16진수 64자이고, CI 원값은 담지 않는다.
 *
 * @param value 소문자 16진수 64자
 */
public record CiHash(String value) {

    public static final int LENGTH = 64;

    private static final Pattern LOWER_HEX = Pattern.compile("[0-9a-f]{" + LENGTH + "}");

    public CiHash {
        if (value == null || !LOWER_HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("CI 해시는 소문자 16진수 " + LENGTH + "자여야 합니다.");
        }
    }

    /** record 기본 toString은 해시를 그대로 출력한다. 로그에 남지 않게 가린다. */
    @Override
    public String toString() {
        return "CiHash[****]";
    }
}
