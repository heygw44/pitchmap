package com.pitchmap.admin.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 신고 처리 요청 본문을 모아 둔 이름 공간이다. */
public final class AdminMemberReportRequests {

    private AdminMemberReportRequests() {}

    /**
     * 신고 조치 요청. sanction이 null이면 제재를 내리지 않는다. 제재도 후기 숨김도 요청하지 않으면 서버가 거부한다.
     *
     * @param hideReview 후기 신고에서 신고된 동행 후기를 숨길지 여부. 생략하거나 null이면 false다
     * @param note 처리 메모(1000자 이하). 없어도 된다
     */
    public record ActionRequest(
            @Valid SanctionRequest sanction,
            Boolean hideReview,
            @Size(max = 1000) String note) {

        /** 호출하면 후기를 숨기라는 요청인지 돌려준다. hideReview를 생략했으면 false로 본다. */
        public boolean shouldHideReview() {
            return Boolean.TRUE.equals(hideReview);
        }
    }

    /**
     * 내릴 제재.
     *
     * @param type WARNING, SUSPEND_7D, SUSPEND_30D, PERMANENT 중 하나
     * @param reason 제재 사유(1~500자)
     */
    public record SanctionRequest(
            @NotBlank String type,
            @NotBlank @Size(max = 500) String reason) {}

    /** 신고 기각 요청. 본문 전체를 생략해도 된다. */
    public record DismissRequest(@Size(max = 1000) String note) {}
}
