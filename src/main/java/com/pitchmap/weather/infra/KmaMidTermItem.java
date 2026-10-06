package com.pitchmap.weather.infra;

import java.util.Map;

/**
 * 중기예보 응답의 항목 하나(구역 하나의 4~10일 예보). 필드 이름과 값을 원천 그대로 담는다.
 *
 * @param fields 예: 육상예보의 {@code wf4Am}, {@code rnSt4Am}, 기온예보의 {@code taMin4}. 원천이 주지 않은 필드는 들어 있지 않다.
 */
public record KmaMidTermItem(Map<String, String> fields) {

    public KmaMidTermItem {
        fields = Map.copyOf(fields);
    }

    /** 값이 없으면 {@code null}이다. */
    public String get(String field) {
        return fields.get(field);
    }
}
