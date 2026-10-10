package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ReportContentCleanupIntegrationTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    @Autowired
    private ReportContentCleanupService cleanupService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private CompanionReviewFixture fixture;
    private MemberReportFixture reportFixture;
    private long basecampId;
    private long targetId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        reportFixture = new MemberReportFixture(jdbc);
        basecampId = fixture.saveBasecamp("COMPLETED", clock.instant().minus(Duration.ofDays(400)));
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @Test
    @DisplayName("[F-21][PV-07] 조치·기각한 지 1년이 지난 신고는 내용과 처리 메모를 지우고 행은 남긴다")
    void clearsHandledReportsOlderThanOneYear() {
        long actioned = handledReport("ACTIONED", cutoff().minus(ONE_SECOND));
        long dismissed = handledReport("DISMISSED", cutoff().minus(Duration.ofDays(30)));

        ReportContentCleanupService.Result result = cleanupService.clearExpiredContent();

        assertThat(result.reports()).isEqualTo(2);
        assertThat(contentErased(actioned)).isTrue();
        assertThat(contentErased(dismissed)).isTrue();
        assertThat(count("member_report WHERE id IN (?, ?)", actioned, dismissed))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("[F-21][PV-07] 정확히 1년 된 처리 완료 신고는 지우고, 1년이 되기 1초 전인 신고와 접수·검토 중인 신고는 남긴다")
    void keepsRecentAndUnfinishedReports() {
        long exact = handledReport("ACTIONED", cutoff());
        long justUnder = handledReport("ACTIONED", cutoff().plus(ONE_SECOND));
        long received = unfinishedReport("RECEIVED");
        long inReview = unfinishedReport("IN_REVIEW");

        ReportContentCleanupService.Result result = cleanupService.clearExpiredContent();

        assertThat(result.reports()).isEqualTo(1);
        assertThat(contentErased(exact)).isTrue();
        assertThat(contentErased(justUnder)).isFalse();
        assertThat(contentErased(received)).isFalse();
        assertThat(contentErased(inReview)).isFalse();
    }

    @Test
    @DisplayName("[F-21][PV-07] 내용을 지운 신고의 updated_at은 바꾸지 않고, 같은 작업을 다시 돌리면 0건이다")
    void keepsUpdatedAtAndIsIdempotent() {
        long report = handledReport("DISMISSED", cutoff().minus(ONE_SECOND));
        Object updatedAtBefore =
                jdbc.queryForObject("SELECT updated_at FROM member_report WHERE id = ?", Object.class, report);

        cleanupService.clearExpiredContent();
        ReportContentCleanupService.Result again = cleanupService.clearExpiredContent();

        Object updatedAtAfter =
                jdbc.queryForObject("SELECT updated_at FROM member_report WHERE id = ?", Object.class, report);
        assertThat(updatedAtAfter).isEqualTo(updatedAtBefore);
        assertThat(again.reports()).isZero();
    }

    @Test
    @DisplayName("[F-16][PV-07] 기간이 끝난 지 1년이 지난 정지의 사유를 지운다")
    void clearsExpiredSuspensionReason() {
        Instant cutoff = cutoff();
        long older =
                sanction("SUSPEND_7D", "EXPIRED", cutoff.minus(Duration.ofDays(8)), cutoff.minus(ONE_SECOND), null);
        long exact = sanction("SUSPEND_7D", "EXPIRED", cutoff.minus(Duration.ofDays(7)), cutoff, null);
        long recent =
                sanction("SUSPEND_7D", "EXPIRED", cutoff.minus(Duration.ofDays(6)), cutoff.plus(ONE_SECOND), null);

        ReportContentCleanupService.Result result = cleanupService.clearExpiredContent();

        assertThat(result.sanctions()).isEqualTo(2);
        assertThat(reasonErased(older)).isTrue();
        assertThat(reasonErased(exact)).isTrue();
        assertThat(reasonErased(recent)).isFalse();
    }

    @Test
    @DisplayName("[F-16][PV-07] 해제한 지 1년이 지난 제재의 사유를 지우고, 최근에 해제한 제재는 남긴다")
    void clearsLiftedSanctionReason() {
        Instant cutoff = cutoff();
        long older =
                sanction("SUSPEND_30D", "LIFTED", cutoff.minus(Duration.ofDays(60)), null, cutoff.minus(ONE_SECOND));
        long recent =
                sanction("SUSPEND_30D", "LIFTED", cutoff.minus(Duration.ofDays(60)), null, cutoff.plus(ONE_SECOND));

        ReportContentCleanupService.Result result = cleanupService.clearExpiredContent();

        assertThat(result.sanctions()).isEqualTo(1);
        assertThat(reasonErased(older)).isTrue();
        assertThat(reasonErased(recent)).isFalse();
    }

    @Test
    @DisplayName("[F-16][PV-07] 경고는 내린 지 1년이 지나면 적용 중 상태여도 사유를 지우고, 최근 경고는 남긴다")
    void clearsWarningReasonAfterOneYearFromStart() {
        Instant cutoff = cutoff();
        long older = sanction("WARNING", "ACTIVE", cutoff.minus(ONE_SECOND), null, null);
        long recent = sanction("WARNING", "ACTIVE", cutoff.plus(ONE_SECOND), null, null);

        ReportContentCleanupService.Result result = cleanupService.clearExpiredContent();

        assertThat(result.sanctions()).isEqualTo(1);
        assertThat(reasonErased(older)).isTrue();
        assertThat(reasonErased(recent)).isFalse();
    }

    @Test
    @DisplayName("[F-16][PV-07] 영구 정지와 적용 중인 정지는 오래돼도 사유를 지우지 않는다")
    void keepsPermanentAndActiveSuspensionReason() {
        Instant longAgo = cutoff().minus(Duration.ofDays(1000));
        long permanent = sanction("PERMANENT", "ACTIVE", longAgo, null, null);
        long activeSuspension = sanction("SUSPEND_30D", "ACTIVE", longAgo, longAgo.plus(Duration.ofDays(30)), null);

        ReportContentCleanupService.Result result = cleanupService.clearExpiredContent();

        assertThat(result.sanctions()).isZero();
        assertThat(reasonErased(permanent)).isFalse();
        assertThat(reasonErased(activeSuspension)).isFalse();
    }

    @Test
    @DisplayName("[F-21][PV-07] 한 번에 바꾸는 상한보다 대상이 많아도 모두 지운다")
    void clearsAllRowsBeyondBatchSize() {
        int total = ReportContentCleanupService.BATCH_SIZE + 1;
        Instant old = cutoff().minus(Duration.ofDays(30));
        for (int i = 0; i < total; i++) {
            sanction("WARNING", "ACTIVE", old, null, null);
        }

        ReportContentCleanupService.Result result = cleanupService.clearExpiredContent();

        assertThat(result.sanctions()).isEqualTo(total);
        assertThat(count("sanction WHERE reason IS NOT NULL")).isZero();
    }

    private Instant cutoff() {
        return clock.instant()
                .atZone(ZoneOffset.UTC)
                .minus(ReportContentCleanupService.RETENTION)
                .toInstant();
    }

    private long handledReport(String status, Instant handledAt) {
        long reportId = unfinishedReport(status);
        jdbc.update(
                "UPDATE member_report SET handled_at = ?, result_note = '처리 메모' WHERE id = ?",
                DATETIME_UTC.format(handledAt),
                reportId);
        return reportId;
    }

    private long unfinishedReport(String status) {
        long reporterId = fixture.saveVerifiedMember(TestSequence.nickname());
        long reportId =
                reportFixture.insertMemberReport(reporterId, targetId, basecampId, "NO_SHOW", status, clock.instant());
        jdbc.update(
                "UPDATE member_report SET result_note = '처리 메모' WHERE id = ? AND status IN ('ACTIONED', 'DISMISSED')",
                reportId);
        return reportId;
    }

    private long sanction(String type, String status, Instant startsAt, Instant endsAt, Instant liftedAt) {
        jdbc.update(
                "INSERT INTO sanction (member_id, type, reason, starts_at, ends_at, status, lifted_at, created_at,"
                        + " updated_at) VALUES (?, ?, '제재 사유', ?, ?, ?, ?, ?, ?)",
                targetId,
                type,
                DATETIME_UTC.format(startsAt),
                endsAt == null ? null : DATETIME_UTC.format(endsAt),
                status,
                liftedAt == null ? null : DATETIME_UTC.format(liftedAt),
                DATETIME_UTC.format(startsAt),
                DATETIME_UTC.format(startsAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM sanction", Long.class);
    }

    private boolean contentErased(long reportId) {
        return count("member_report WHERE id = ? AND content IS NULL AND result_note IS NULL", reportId) == 1;
    }

    private boolean reasonErased(long sanctionId) {
        return count("sanction WHERE id = ? AND reason IS NULL", sanctionId) == 1;
    }

    private int count(String fromAndWhere, Object... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class, args);
        return count == null ? 0 : count;
    }
}
