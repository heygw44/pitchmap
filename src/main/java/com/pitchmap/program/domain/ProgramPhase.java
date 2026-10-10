package com.pitchmap.program.domain;

import java.time.Instant;

/** 공개 화면에 보여 주는 행사의 진행 단계다. 저장하지 않고 행사 상태와 신청 시각으로 매번 계산한다. */
public enum ProgramPhase {
    /** 신청 시작 전. */
    UPCOMING,
    /** 신청 시작 시각 이상, 마감 시각 미만. */
    OPEN,
    /** 신청 마감 시각 이후. */
    CLOSED,
    /** 관리자가 취소한 행사. */
    CANCELED;

    /** 호출하면 취소된 행사는 CANCELED, 아니면 now가 신청 시작 전, 신청 기간 안, 마감 후인지에 따라 UPCOMING, OPEN, CLOSED를 돌려준다. */
    public static ProgramPhase of(ProgramStatus status, Instant applyOpenAt, Instant applyCloseAt, Instant now) {
        if (status == ProgramStatus.CANCELED) {
            return CANCELED;
        }
        if (now.isBefore(applyOpenAt)) {
            return UPCOMING;
        }
        if (now.isBefore(applyCloseAt)) {
            return OPEN;
        }
        return CLOSED;
    }
}
