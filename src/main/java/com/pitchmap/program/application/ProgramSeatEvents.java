package com.pitchmap.program.application;

import java.util.List;

/**
 * 행사에 빈자리가 돌아온 이벤트의 종류와 내용. 자리 반환 기록기가 기록하고 알림 신청자에게 알리는 처리기가 읽으므로
 * 이름과 형태를 한 곳에 둔다.
 */
public final class ProgramSeatEvents {

    public static final String SEAT_RELEASED_EVENT_TYPE = "PROGRAM_SEAT_RELEASED";
    public static final String AGGREGATE_TYPE = "PROGRAM";

    private ProgramSeatEvents() {}

    /**
     * @param programId 자리가 돌아온 행사 ID
     * @param memberIds 알림을 받을 회원 ID. 빈자리 알림을 신청했고 결제 대기·확정 신청이 없는 회원이다.
     */
    public record SeatReleasedPayload(long programId, List<Long> memberIds) {}
}
