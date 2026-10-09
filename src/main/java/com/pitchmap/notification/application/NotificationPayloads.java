package com.pitchmap.notification.application;

import java.time.Instant;
import java.util.List;

/**
 * 알림을 만드는 이벤트의 payload 형태다. 이벤트를 기록하는 모듈이 JSON으로 싣는 필드 이름과 같아야 한다.
 * notification 모듈은 그 모듈의 클래스를 가져다 쓰지 않으므로 필요한 필드만 따로 정의한다. 모르는 필드는 읽을 때 무시한다.
 */
final class NotificationPayloads {

    private NotificationPayloads() {}

    /** 합류 신청 이벤트. 캠프 리더에게 알린다. */
    record Applied(long applicationId, long applicantId, long leaderId) {}

    /** 합류 승인·거절 이벤트. 신청자에게 알린다. */
    record Decision(long applicationId, long applicantId, long basecampId) {}

    /** 강퇴 이벤트. reason은 강퇴 사유 이름이다. */
    record Kicked(long basecampId, long memberId, String reason) {}

    /** 베이스캠프의 확정·취소·완료·멤버 변경 이벤트. memberIds 전원에게 알린다. */
    record BasecampMembers(long basecampId, List<Long> memberIds) {}

    /** 신고 처리 이벤트. result는 ACTIONED(조치) 또는 DISMISSED(기각)이다. */
    record ReportResolved(long reportId, long reporterId, String result) {}

    /** 제재 확정 이벤트. endsAt은 기간이 정해진 정지일 때만 값이 있다. */
    record SanctionConfirmed(long memberId, long sanctionId, String sanctionType, Instant endsAt) {}

    /** 행사 신청 확정 이벤트. 신청자에게 알린다. */
    record ProgramApplicationConfirmed(long applicationId, long memberId, long programId) {}

    /** 행사 신청 취소 이벤트. reason은 USER(본인 취소) 또는 PROGRAM_CANCELED(행사 취소)이고, refunded는 결제를 환불했는지 여부다. */
    record ProgramApplicationCanceled(
            long applicationId, long memberId, long programId, String reason, boolean refunded) {}
}
