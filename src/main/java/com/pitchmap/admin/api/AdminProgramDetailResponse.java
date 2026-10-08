package com.pitchmap.admin.api;

import com.pitchmap.program.application.ProgramDetail;
import java.time.Instant;

/**
 * 관리자가 행사를 고친 뒤 받는 상세다. 공개 상세와 같은 모양이다. 다만 관리자는 자기 신청을 확인할 일이 없어서 myApplication을 뺀다.
 * status는 UPCOMING, OPEN, CLOSED, CANCELED 중 하나이고, spotId는 지도 장소와 연결하지 않았으면 null이다.
 */
public record AdminProgramDetailResponse(
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
        String status) {

    static AdminProgramDetailResponse from(ProgramDetail detail) {
        return new AdminProgramDetailResponse(
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
                detail.status());
    }
}
