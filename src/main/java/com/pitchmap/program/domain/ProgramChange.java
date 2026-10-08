package com.pitchmap.program.domain;

import java.util.List;

/**
 * 행사를 수정한 결과다. changedFields는 값이 실제로 바뀐 필드의 이름이고, 정원은 바뀌지 않았어도 수정 전후 값을 함께 담는다.
 */
public record ProgramChange(List<String> changedFields, int capacityBefore, int capacityAfter) {

    public boolean isCapacityChanged() {
        return capacityBefore != capacityAfter;
    }
}
