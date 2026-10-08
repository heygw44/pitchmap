package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberReportTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");

    @Test
    @DisplayName("[SN-01] 회원 신고를 만들면 접수 상태이고, 종류는 유형에서 정해지며 후기 ID는 없다")
    void receiveMemberReport() {
        MemberReport report = MemberReport.receive(10L, 11L, 3L, null, ReportType.NO_SHOW, "약속 장소에 오지 않았다", NOW);

        assertThat(report.getReporterId()).isEqualTo(10L);
        assertThat(report.getTargetMemberId()).isEqualTo(11L);
        assertThat(report.getBasecampId()).isEqualTo(3L);
        assertThat(report.getKind()).isEqualTo(ReportKind.MEMBER);
        assertThat(report.getCompanionReviewId()).isNull();
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(report.isUrgent()).isFalse();
        assertThat(report.getCreatedAt()).isEqualTo(NOW);
        assertThat(report.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[SN-05] 성희롱·위협 신고는 긴급 신고로 만들어진다")
    void harassmentReportIsUrgent() {
        MemberReport report = MemberReport.receive(10L, 11L, 3L, null, ReportType.HARASSMENT_OR_THREAT, "위협했다", NOW);

        assertThat(report.isUrgent()).isTrue();
    }

    @Test
    @DisplayName("[SN-07] 후기 신고는 REVIEW 종류이고 동행 후기 ID를 담는다")
    void receiveReviewReport() {
        MemberReport report = MemberReport.receive(10L, 11L, 3L, 77L, ReportType.INAPPROPRIATE_REVIEW, "허위 후기", NOW);

        assertThat(report.getKind()).isEqualTo(ReportKind.REVIEW);
        assertThat(report.getCompanionReviewId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("후기 신고에 후기 ID가 없거나 회원 신고에 후기 ID가 있으면 만들지 못한다")
    void reviewIdMustMatchKind() {
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, null, ReportType.INAPPROPRIATE_REVIEW, "내용", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, 77L, ReportType.NO_SHOW, "내용", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("자기 자신을 신고하거나 내용이 비었거나 1000자를 넘으면 만들지 못한다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> MemberReport.receive(10L, 10L, 3L, null, ReportType.NO_SHOW, "내용", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, null, ReportType.NO_SHOW, " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, null, ReportType.NO_SHOW, "가".repeat(1001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberReport.receive(10L, 11L, 3L, null, null, "내용", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MemberReport.receive(10L, 11L, 3L, null, ReportType.NO_SHOW, "가".repeat(1000), NOW))
                .isNotNull();
    }
}
