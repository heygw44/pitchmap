package com.pitchmap.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NotificationTemplatesTest {

    private static final long BASECAMP_ID = 30L;

    @Test
    @DisplayName("[F-20] 합류 신청 이벤트는 캠프 리더에게만 알리고 링크는 베이스캠프 관리 화면이다")
    void appliedNotifiesLeaderOnly() {
        // given
        NotificationPayloads.Applied payload = new NotificationPayloads.Applied(1L, 2L, 3L);

        // when
        List<NotificationDraft> drafts = NotificationTemplates.applied(BASECAMP_ID, payload);

        // then
        assertThat(drafts)
                .containsExactly(new NotificationDraft(
                        3L,
                        "BASECAMP_APPLIED",
                        "새 합류 신청",
                        "베이스캠프에 합류 신청이 들어왔습니다. 신청자를 확인하고 승인하거나 거절해 주세요.",
                        "/basecamps/30/manage"));
    }

    @Test
    @DisplayName("[F-20] 합류 승인과 거절 이벤트는 신청자에게만 알린다")
    void decisionNotifiesApplicantOnly() {
        // given
        NotificationPayloads.Decision payload = new NotificationPayloads.Decision(1L, 2L, BASECAMP_ID);

        // when
        List<NotificationDraft> approved = NotificationTemplates.approved(payload);
        List<NotificationDraft> rejected = NotificationTemplates.rejected(payload);

        // then
        assertThat(approved)
                .containsExactly(new NotificationDraft(
                        2L, "BASECAMP_APPROVED", "합류 승인", "합류 신청이 승인되어 베이스캠프 멤버가 됐습니다.", "/basecamps/30"));
        assertThat(rejected)
                .containsExactly(
                        new NotificationDraft(2L, "BASECAMP_REJECTED", "합류 거절", "합류 신청이 거절됐습니다.", "/basecamps/30"));
    }

    @ParameterizedTest
    @CsvSource({"NO_CONTACT, 연락 두절", "CONDITION_MISMATCH, 일정·조건 불일치", "INAPPROPRIATE_BEHAVIOR, 부적절한 언행", "OTHER, 기타"})
    @DisplayName("[F-20][BC-07] 강퇴 알림 본문에는 사유 이름에 맞는 한글 사유와 재신청 불가 안내가 들어간다")
    void kickedBodyContainsReasonLabel(String reason, String label) {
        // given
        NotificationPayloads.Kicked payload = new NotificationPayloads.Kicked(BASECAMP_ID, 7L, reason);

        // when
        List<NotificationDraft> drafts = NotificationTemplates.kicked(payload);

        // then
        assertThat(drafts).hasSize(1);
        NotificationDraft draft = drafts.get(0);
        assertThat(draft.memberId()).isEqualTo(7L);
        assertThat(draft.type()).isEqualTo("BASECAMP_KICKED");
        assertThat(draft.title()).isEqualTo("베이스캠프에서 제외됨");
        assertThat(draft.body()).contains("사유: " + label + ".").contains("같은 베이스캠프에는 다시 신청할 수 없습니다.");
        assertThat(draft.link()).isEqualTo("/basecamps/30");
    }

    @Test
    @DisplayName("[F-20] 모르는 강퇴 사유 이름이면 예외를 던진다")
    void unknownKickReasonThrows() {
        NotificationPayloads.Kicked payload = new NotificationPayloads.Kicked(BASECAMP_ID, 7L, "UNKNOWN");

        assertThatThrownBy(() -> NotificationTemplates.kicked(payload)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-20] 베이스캠프 확정 알림은 멤버 전원에게 가고 안전 수칙을 한 건에 함께 담는다")
    void confirmedNotifiesAllMembersWithSafetyRules() {
        // given
        NotificationPayloads.BasecampMembers payload =
                new NotificationPayloads.BasecampMembers(BASECAMP_ID, List.of(1L, 2L, 3L));

        // when
        List<NotificationDraft> drafts = NotificationTemplates.confirmed(payload);

        // then
        assertThat(drafts).extracting(NotificationDraft::memberId).containsExactly(1L, 2L, 3L);
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.type()).isEqualTo("BASECAMP_CONFIRMED");
            assertThat(draft.title()).isEqualTo("베이스캠프 확정");
            assertThat(draft.body()).contains("확정됐습니다").contains("안전 수칙");
            assertThat(draft.link()).isEqualTo("/basecamps/30");
        });
    }

    @Test
    @DisplayName("[F-20] 베이스캠프 취소와 멤버 변경 알림은 멤버 전원에게 가고 링크는 베이스캠프다")
    void canceledAndMemberChangedNotifyAllMembers() {
        // given
        NotificationPayloads.BasecampMembers payload =
                new NotificationPayloads.BasecampMembers(BASECAMP_ID, List.of(4L, 5L));

        // when
        List<NotificationDraft> canceled = NotificationTemplates.canceled(payload);
        List<NotificationDraft> changed = NotificationTemplates.memberChanged(payload);

        // then
        assertThat(canceled).extracting(NotificationDraft::memberId).containsExactly(4L, 5L);
        assertThat(canceled)
                .allSatisfy(draft -> assertThat(draft)
                        .extracting(NotificationDraft::type, NotificationDraft::title, NotificationDraft::link)
                        .containsExactly("BASECAMP_CANCELED", "베이스캠프 취소", "/basecamps/30"));
        assertThat(changed).extracting(NotificationDraft::memberId).containsExactly(4L, 5L);
        assertThat(changed)
                .allSatisfy(draft -> assertThat(draft)
                        .extracting(NotificationDraft::type, NotificationDraft::title, NotificationDraft::link)
                        .containsExactly("BASECAMP_MEMBER_CHANGED", "멤버 변경", "/basecamps/30"));
    }

    @Test
    @DisplayName("[F-20][RV-02] 베이스캠프 완료 알림은 멤버 전원에게 가고 동행 후기 요청을 한 건에 함께 담는다")
    void completedAsksForCompanionReview() {
        // given
        NotificationPayloads.BasecampMembers payload =
                new NotificationPayloads.BasecampMembers(BASECAMP_ID, List.of(8L, 9L));

        // when
        List<NotificationDraft> drafts = NotificationTemplates.completed(payload);

        // then
        assertThat(drafts).extracting(NotificationDraft::memberId).containsExactly(8L, 9L);
        assertThat(drafts).allSatisfy(draft -> {
            assertThat(draft.type()).isEqualTo("BASECAMP_COMPLETED");
            assertThat(draft.body()).contains("14일 안에").contains("동행 후기");
            assertThat(draft.link()).isEqualTo("/me/companion-reviews");
        });
    }

    @Test
    @DisplayName("[F-20] 멤버가 없는 이벤트는 알림을 만들지 않는다")
    void emptyMembersMakeNoDrafts() {
        NotificationPayloads.BasecampMembers payload = new NotificationPayloads.BasecampMembers(BASECAMP_ID, List.of());

        assertThat(NotificationTemplates.confirmed(payload)).isEmpty();
    }

    @Test
    @DisplayName("[F-20] 신고 결과 알림은 신고자에게만 가고 조치와 기각 문구가 다르며 제재 내용이 없다")
    void reportResolvedHasResultOnly() {
        // given
        NotificationPayloads.ReportResolved actioned = new NotificationPayloads.ReportResolved(1L, 6L, "ACTIONED");
        NotificationPayloads.ReportResolved dismissed = new NotificationPayloads.ReportResolved(2L, 6L, "DISMISSED");

        // when
        NotificationDraft actionedDraft =
                NotificationTemplates.reportResolved(actioned).get(0);
        NotificationDraft dismissedDraft =
                NotificationTemplates.reportResolved(dismissed).get(0);

        // then
        assertThat(actionedDraft.memberId()).isEqualTo(6L);
        assertThat(actionedDraft.type()).isEqualTo("MEMBER_REPORT_RESOLVED");
        assertThat(actionedDraft.title()).isEqualTo("신고 처리 완료");
        assertThat(actionedDraft.body()).isEqualTo("신고하신 내용을 검토해 조치했습니다.");
        assertThat(dismissedDraft.body()).isEqualTo("신고하신 내용을 검토했지만 조치 대상이 아니라고 판단했습니다.");
        assertThat(List.of(actionedDraft, dismissedDraft)).allSatisfy(draft -> {
            assertThat(draft.link()).isNull();
            assertThat(draft.body()).doesNotContain("경고", "정지", "제재");
        });
    }

    @Test
    @DisplayName("[F-20] 모르는 신고 결과 값이면 예외를 던진다")
    void unknownReportResultThrows() {
        NotificationPayloads.ReportResolved payload = new NotificationPayloads.ReportResolved(1L, 6L, "UNKNOWN");

        assertThatThrownBy(() -> NotificationTemplates.reportResolved(payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-20] 경고와 영구 정지 알림은 종료 시각 없이 종류에 맞는 문구를 쓴다")
    void warningAndPermanentSanctionBodies() {
        // given
        NotificationPayloads.SanctionConfirmed warning =
                new NotificationPayloads.SanctionConfirmed(5L, 1L, "WARNING", null);
        NotificationPayloads.SanctionConfirmed permanent =
                new NotificationPayloads.SanctionConfirmed(5L, 2L, "PERMANENT", null);

        // when
        NotificationDraft warningDraft =
                NotificationTemplates.sanctionConfirmed(warning).get(0);
        NotificationDraft permanentDraft =
                NotificationTemplates.sanctionConfirmed(permanent).get(0);

        // then
        assertThat(warningDraft.memberId()).isEqualTo(5L);
        assertThat(warningDraft.type()).isEqualTo("SANCTION_CONFIRMED");
        assertThat(warningDraft.title()).isEqualTo("이용 제재 안내");
        assertThat(warningDraft.body()).isEqualTo("경고를 받았습니다.");
        assertThat(warningDraft.link()).isNull();
        assertThat(permanentDraft.body()).isEqualTo("이용이 영구 정지됐습니다.");
    }

    @ParameterizedTest
    @CsvSource({"SUSPEND_7D", "SUSPEND_30D"})
    @DisplayName("[F-20] 기간 정지 알림은 종료 시각을 한국 시간 yyyy-MM-dd HH:mm으로 알린다")
    void timedSuspensionBodyShowsEndInKoreanTime(String sanctionType) {
        // given: UTC 2026-10-12 03:00은 한국 시간으로 같은 날 12:00이다.
        NotificationPayloads.SanctionConfirmed payload =
                new NotificationPayloads.SanctionConfirmed(5L, 3L, sanctionType, Instant.parse("2026-10-12T03:00:00Z"));

        // when
        NotificationDraft draft =
                NotificationTemplates.sanctionConfirmed(payload).get(0);

        // then
        assertThat(draft.body()).isEqualTo("2026-10-12 12:00까지 이용이 정지됩니다.");
    }

    @Test
    @DisplayName("[F-20] 기간 정지인데 종료 시각이 없거나 모르는 제재 종류이면 예외를 던진다")
    void invalidSanctionPayloadThrows() {
        NotificationPayloads.SanctionConfirmed noEndsAt =
                new NotificationPayloads.SanctionConfirmed(5L, 3L, "SUSPEND_7D", null);
        NotificationPayloads.SanctionConfirmed unknown =
                new NotificationPayloads.SanctionConfirmed(5L, 3L, "UNKNOWN", null);

        assertThatThrownBy(() -> NotificationTemplates.sanctionConfirmed(noEndsAt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationTemplates.sanctionConfirmed(unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
