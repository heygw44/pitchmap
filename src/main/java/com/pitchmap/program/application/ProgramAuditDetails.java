package com.pitchmap.program.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 관리자 조치의 감사 로그 detail로 저장할 값. 행사의 제목·설명 원문은 담지 않는다. 값이 없는 필드는 JSON에서 뺀다. */
public final class ProgramAuditDetails {

    private ProgramAuditDetails() {}

    public record Create(int capacity, boolean overnight) {}

    /** 정원이 바뀐 경우에만 capacityBefore와 capacityAfter를 담는다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Update(List<String> changedFields, Integer capacityBefore, Integer capacityAfter) {}

    public record Cancel(String fromStatus, String toStatus, int canceledApplicationCount, int refundedPaymentCount) {}
}
