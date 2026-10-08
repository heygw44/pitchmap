package com.pitchmap.notification.application;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 이벤트 내용을 받는 사람별 알림 내용으로 바꾼다. 저장하거나 읽지 않고 문구만 만드는 순수한 클래스다.
 * 신고 결과 알림에는 제재 내용을 담지 않는다. 신고자에게는 처리 결과만 알린다.
 */
final class NotificationTemplates {

    private static final DateTimeFormatter SUSPENSION_END_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    private NotificationTemplates() {}

    /** 호출하면 basecampId인 베이스캠프에 합류 신청이 들어왔다고 캠프 리더에게 알리는 알림을 만든다. */
    static List<NotificationDraft> applied(long basecampId, NotificationPayloads.Applied payload) {
        return List.of(new NotificationDraft(
                payload.leaderId(),
                NotificationEventTypes.BASECAMP_APPLIED,
                "새 합류 신청",
                "베이스캠프에 합류 신청이 들어왔습니다. 신청자를 확인하고 승인하거나 거절해 주세요.",
                "/basecamps/" + basecampId + "/manage"));
    }

    static List<NotificationDraft> approved(NotificationPayloads.Decision payload) {
        return List.of(new NotificationDraft(
                payload.applicantId(),
                NotificationEventTypes.BASECAMP_APPROVED,
                "합류 승인",
                "합류 신청이 승인되어 베이스캠프 멤버가 됐습니다.",
                basecampLink(payload.basecampId())));
    }

    static List<NotificationDraft> rejected(NotificationPayloads.Decision payload) {
        return List.of(new NotificationDraft(
                payload.applicantId(),
                NotificationEventTypes.BASECAMP_REJECTED,
                "합류 거절",
                "합류 신청이 거절됐습니다.",
                basecampLink(payload.basecampId())));
    }

    /** 호출하면 강퇴 사유를 담아 강퇴된 회원에게 알리는 알림을 만든다. 모르는 사유 이름이면 예외를 던진다. */
    static List<NotificationDraft> kicked(NotificationPayloads.Kicked payload) {
        String body = "캠프 리더가 베이스캠프에서 제외했습니다. 사유: " + kickReasonLabel(payload.reason()) + ". 같은 베이스캠프에는 다시 신청할 수 없습니다.";
        return List.of(new NotificationDraft(
                payload.memberId(),
                NotificationEventTypes.BASECAMP_KICKED,
                "베이스캠프에서 제외됨",
                body,
                basecampLink(payload.basecampId())));
    }

    static List<NotificationDraft> confirmed(NotificationPayloads.BasecampMembers payload) {
        return toMembers(
                payload,
                NotificationEventTypes.BASECAMP_CONFIRMED,
                "베이스캠프 확정",
                "베이스캠프가 확정됐습니다. 안전 수칙: 일정을 가족·지인과 공유하고, 단독 행동을 자제하고, 흔적을 남기지 마세요.",
                basecampLink(payload.basecampId()));
    }

    static List<NotificationDraft> canceled(NotificationPayloads.BasecampMembers payload) {
        return toMembers(
                payload,
                NotificationEventTypes.BASECAMP_CANCELED,
                "베이스캠프 취소",
                "참여 중인 베이스캠프가 취소됐습니다.",
                basecampLink(payload.basecampId()));
    }

    static List<NotificationDraft> completed(NotificationPayloads.BasecampMembers payload) {
        return toMembers(
                payload,
                NotificationEventTypes.BASECAMP_COMPLETED,
                "베이스캠프 완료",
                "베이스캠프가 완료됐습니다. 14일 안에 함께한 멤버의 동행 후기를 남겨 주세요.",
                "/me/companion-reviews");
    }

    static List<NotificationDraft> memberChanged(NotificationPayloads.BasecampMembers payload) {
        return toMembers(
                payload,
                NotificationEventTypes.BASECAMP_MEMBER_CHANGED,
                "멤버 변경",
                "멤버 사정으로 베이스캠프 구성이 변경됐습니다.",
                basecampLink(payload.basecampId()));
    }

    /** 호출하면 신고 처리 결과만 신고자에게 알린다. 제재 종류나 사유는 어디에도 싣지 않는다. 모르는 결과 값이면 예외를 던진다. */
    static List<NotificationDraft> reportResolved(NotificationPayloads.ReportResolved payload) {
        String body = switch (payload.result()) {
            case "ACTIONED" -> "신고하신 내용을 검토해 조치했습니다.";
            case "DISMISSED" -> "신고하신 내용을 검토했지만 조치 대상이 아니라고 판단했습니다.";
            default -> throw new IllegalArgumentException("알 수 없는 신고 처리 결과입니다: " + payload.result());
        };
        return List.of(new NotificationDraft(
                payload.reporterId(), NotificationEventTypes.MEMBER_REPORT_RESOLVED, "신고 처리 완료", body, null));
    }

    /** 호출하면 제재 종류에 맞는 문구로 제재받은 회원에게 알린다. 기간이 정해진 정지인데 endsAt이 없거나 모르는 종류이면 예외를 던진다. */
    static List<NotificationDraft> sanctionConfirmed(NotificationPayloads.SanctionConfirmed payload) {
        return List.of(new NotificationDraft(
                payload.memberId(),
                NotificationEventTypes.SANCTION_CONFIRMED,
                "이용 제재 안내",
                sanctionBody(payload),
                null));
    }

    private static String sanctionBody(NotificationPayloads.SanctionConfirmed payload) {
        return switch (payload.sanctionType()) {
            case "WARNING" -> "경고를 받았습니다.";
            case "PERMANENT" -> "이용이 영구 정지됐습니다.";
            case "SUSPEND_7D", "SUSPEND_30D" -> {
                if (payload.endsAt() == null) {
                    throw new IllegalArgumentException("정지 종료 시각이 없습니다. sanctionId=" + payload.sanctionId());
                }
                yield SUSPENSION_END_FORMAT.format(payload.endsAt()) + "까지 이용이 정지됩니다.";
            }
            default -> throw new IllegalArgumentException("알 수 없는 제재 종류입니다: " + payload.sanctionType());
        };
    }

    private static String kickReasonLabel(String reason) {
        return switch (reason) {
            case "NO_CONTACT" -> "연락 두절";
            case "CONDITION_MISMATCH" -> "일정·조건 불일치";
            case "INAPPROPRIATE_BEHAVIOR" -> "부적절한 언행";
            case "OTHER" -> "기타";
            default -> throw new IllegalArgumentException("알 수 없는 강퇴 사유입니다: " + reason);
        };
    }

    private static List<NotificationDraft> toMembers(
            NotificationPayloads.BasecampMembers payload, String type, String title, String body, String link) {
        return payload.memberIds().stream()
                .map(memberId -> new NotificationDraft(memberId, type, title, body, link))
                .toList();
    }

    private static String basecampLink(long basecampId) {
        return "/basecamps/" + basecampId;
    }
}
