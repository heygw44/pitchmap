package com.pitchmap.program.application;

/**
 * 행사 신청이 확정, 취소, 만료된 이벤트의 종류와 내용. 결제·취소·행사 취소·결제 만료 서비스가 기록하고 신청자에게 알리는 처리기가 읽으므로
 * 이름과 형태를 한 곳에 둔다. 이메일 같은 개인정보는 싣지 않는다.
 */
public final class ProgramApplicationEvents {

    public static final String CONFIRMED_EVENT_TYPE = "PROGRAM_APPLICATION_CONFIRMED";
    public static final String CANCELED_EVENT_TYPE = "PROGRAM_APPLICATION_CANCELED";
    public static final String EXPIRED_EVENT_TYPE = "PROGRAM_APPLICATION_EXPIRED";
    public static final String AGGREGATE_TYPE = "PROGRAM_APPLICATION";

    private ProgramApplicationEvents() {}

    /**
     * @param applicationId 결제해서 확정된 신청 ID
     * @param memberId 알림을 받을 신청자 ID
     * @param programId 신청한 행사 ID
     */
    public record ConfirmedPayload(long applicationId, long memberId, long programId) {}

    /**
     * @param reason 취소 사유 이름. 신청자 본인이 취소하면 USER, 관리자가 행사를 취소하면 PROGRAM_CANCELED
     * @param refunded 결제를 마친 신청이어서 환불했는지 여부
     */
    public record CanceledPayload(long applicationId, long memberId, long programId, String reason, boolean refunded) {}

    /**
     * @param applicationId 결제 기한이 지나 만료된 신청 ID
     * @param memberId 알림을 받을 신청자 ID
     * @param programId 신청한 행사 ID
     */
    public record ExpiredPayload(long applicationId, long memberId, long programId) {}
}
