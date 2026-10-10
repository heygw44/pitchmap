package com.pitchmap.program.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.program.application.ProgramDetail;
import java.time.Instant;

/**
 * 행사 상세다. 비로그인이거나 신청이 없는 요청자에게는 myApplication 필드 자체를 뺀다.
 * status는 UPCOMING, OPEN, CLOSED, CANCELED 중 하나이고, spotId는 지도 장소와 연결하지 않았으면 null이다.
 */
public record ProgramDetailResponse(
        long programId,
        String title,
        String description,
        String locationText,
        Long spotId,
        Instant startAt,
        Instant endAt,
        Instant applyOpenAt,
        Instant applyCloseAt,
        int capacity,
        int remainingSeats,
        int fee,
        int paymentDeadlineMinutes,
        boolean overnight,
        String status,
        @JsonInclude(JsonInclude.Include.NON_NULL) MyApplicationResponse myApplication) {

    static ProgramDetailResponse from(ProgramDetail detail) {
        ProgramDetail.MyApplication mine = detail.myApplication();
        MyApplicationResponse myApplication = mine == null
                ? null
                : new MyApplicationResponse(mine.applicationId(), mine.status(), mine.paymentDueAt());
        return new ProgramDetailResponse(
                detail.programId(),
                detail.title(),
                detail.description(),
                detail.locationText(),
                detail.spotId(),
                detail.startAt(),
                detail.endAt(),
                detail.applyOpenAt(),
                detail.applyCloseAt(),
                detail.capacity(),
                detail.remainingSeats(),
                detail.fee(),
                detail.paymentDeadlineMinutes(),
                detail.overnight(),
                detail.status(),
                myApplication);
    }

    /** 요청한 회원이 이 행사에 낸 가장 최근 신청이다. */
    public record MyApplicationResponse(long applicationId, String status, Instant paymentDueAt) {}
}
