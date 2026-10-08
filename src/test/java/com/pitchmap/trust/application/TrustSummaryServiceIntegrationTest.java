package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class TrustSummaryServiceIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    // 후기 작성 기한(14일)이 지나 받은 후기가 공개되는 완료 시각이다.
    private static final Instant LONG_AGO = NOW.minus(Duration.ofDays(20));
    // 작성 기한이 남아 있어 반대 방향 후기가 없으면 공개되지 않는 완료 시각이다.
    private static final Instant JUST_COMPLETED = NOW.minus(Duration.ofDays(1));
    private static final Duration RECENT_PERIOD = Duration.ofDays(180);

    @Autowired
    private TrustSummaryService trustSummaryService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[TR-01] 완료 동행 3회, 공개된 후기 5건 중 4건이 다시 동행이면 단계 2다")
    void completedThreeAndRejoinEightyPercentIsLevelTwo() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        makeQualified(member);

        assertThat(trustSummaryService.summarize(member).trustLevel()).isEqualTo(2);
        TrustDetail detail = trustSummaryService.detail(member);
        assertThat(detail.trustLevel()).isEqualTo(2);
        assertThat(detail.completedCompanions()).isEqualTo(3);
        assertThat(detail.rejoinRate()).isEqualTo(80);
        assertThat(detail.recentEarlyLeaves()).isZero();
    }

    @Test
    @DisplayName("[TR-01] 완료 동행이 2회뿐이면 후기가 좋아도 단계 1이다")
    void twoCompletedCompanionsIsLevelOne() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        for (int i = 0; i < 2; i++) {
            long basecampId = fixture.saveBasecamp("COMPLETED", LONG_AGO);
            fixture.insertMember(basecampId, member, "MEMBER", "ACTIVE");
            fixture.insertReview(basecampId, fixture.leaderIdOf(basecampId), member, true, LONG_AGO);
        }

        assertThat(trustSummaryService.summarize(member).trustLevel()).isEqualTo(1);
        assertThat(trustSummaryService.detail(member).completedCompanions()).isEqualTo(2);
    }

    @Test
    @DisplayName("[TR-01] 공개 전 후기, 숨긴 후기, 완료되지 않은 베이스캠프, LEFT 멤버 행은 세지 않는다")
    void doesNotCountUnrevealedHiddenNotCompletedOrLeft() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        makeQualified(member);
        // 이 후기들이 세어지면 비율이 80% 아래로 내려간다.
        long unrevealed = fixture.saveBasecamp("COMPLETED", JUST_COMPLETED);
        fixture.insertMember(unrevealed, member, "MEMBER", "ACTIVE");
        long hiddenBasecamp = fixture.saveBasecamp("COMPLETED", LONG_AGO);
        fixture.insertMember(hiddenBasecamp, member, "MEMBER", "ACTIVE");
        long hidden = fixture.insertReview(hiddenBasecamp, fixture.leaderIdOf(hiddenBasecamp), member, false, LONG_AGO);
        fixture.hide(hidden);
        for (int i = 0; i < 3; i++) {
            fixture.insertReview(unrevealed, fixture.saveVerifiedMember(TestSequence.nickname()), member, false, NOW);
        }
        // 완료되지 않은 베이스캠프의 ACTIVE 행과 완료된 베이스캠프의 LEFT 행은 완료한 동행이 아니다.
        long recruiting = fixture.saveBasecamp("RECRUITING", null);
        fixture.insertMember(recruiting, member, "MEMBER", "ACTIVE");
        long completedButLeft = fixture.saveBasecamp("COMPLETED", LONG_AGO);
        fixture.insertMember(completedButLeft, member, "MEMBER", "LEFT");

        TrustDetail detail = trustSummaryService.detail(member);

        // 완료된 ACTIVE 행은 makeQualified의 3개와 공개 전 후기 베이스캠프, 숨긴 후기 베이스캠프를 합쳐 5개다.
        assertThat(detail.completedCompanions()).isEqualTo(5);
        assertThat(detail.rejoinRate()).isEqualTo(80);
        assertThat(detail.trustLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("[RV-03][TR-01] 작성 기한이 남아 있어도 반대 방향 후기가 있으면 그 후기는 센다")
    void countsReviewWhenReverseReviewExists() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        long basecampId = fixture.saveBasecamp("COMPLETED", JUST_COMPLETED);
        fixture.insertMember(basecampId, member, "MEMBER", "ACTIVE");
        long leader = fixture.leaderIdOf(basecampId);
        long other = fixture.saveVerifiedMember(TestSequence.nickname());
        fixture.insertMember(basecampId, other, "MEMBER", "ACTIVE");
        fixture.insertReview(basecampId, leader, member, true, NOW);
        fixture.insertReview(basecampId, member, leader, true, NOW);
        fixture.insertReview(basecampId, other, member, false, NOW);

        TrustDetail detail = trustSummaryService.detail(member);

        // 리더의 후기만 공개된다. other는 member가 쓰지 않았고 기한도 남아 있어 공개되지 않는다.
        assertThat(detail.rejoinRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("[BC-21] 최근 180일 임박 탈퇴가 3회면 다른 조건을 채워도 단계 1이다. 정각 180일 전 기록도 센다")
    void threeRecentEarlyLeavesIsLevelOne() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        makeQualified(member);
        addEarlyLeave(member, NOW.minus(RECENT_PERIOD));
        addEarlyLeave(member, NOW.minus(Duration.ofDays(20)));
        addEarlyLeave(member, NOW.minus(Duration.ofDays(1)));

        assertThat(trustSummaryService.summarize(member).trustLevel()).isEqualTo(1);
        assertThat(trustSummaryService.detail(member).recentEarlyLeaves()).isEqualTo(3);
    }

    @Test
    @DisplayName("[BC-21] 180일보다 1마이크로초 앞선 임박 탈퇴는 세지 않아서 2회가 되고 단계 2를 유지한다")
    void earlyLeaveJustBeforeBoundaryIsNotCounted() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        makeQualified(member);
        addEarlyLeave(member, NOW.minus(RECENT_PERIOD).minusNanos(1_000));
        addEarlyLeave(member, NOW.minus(Duration.ofDays(20)));
        addEarlyLeave(member, NOW.minus(Duration.ofDays(1)));

        assertThat(trustSummaryService.summarize(member).trustLevel()).isEqualTo(2);
        assertThat(trustSummaryService.detail(member).recentEarlyLeaves()).isEqualTo(2);
    }

    @Test
    @DisplayName("[BC-21] 임박 탈퇴로 표시되지 않은 탈퇴 기록은 세지 않는다")
    void ordinaryLeaveIsNotEarlyLeave() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        for (int i = 0; i < 3; i++) {
            long basecampId = fixture.saveBasecamp("CONFIRMED", null);
            fixture.insertMember(basecampId, member, "MEMBER", "LEFT");
        }

        assertThat(trustSummaryService.detail(member).recentEarlyLeaves()).isZero();
    }

    @Test
    @DisplayName("[TR-01][SN-10] 최근 180일 안에 시작한 경고가 있으면 다른 조건을 채워도 단계 1이고, 정각 180일 전 경고도 센다")
    void recentWarningKeepsLevelOne() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        makeQualified(member);
        insertSanction(member, "WARNING", "ACTIVE", NOW.minus(RECENT_PERIOD));

        assertThat(trustSummaryService.summarize(member).trustLevel()).isEqualTo(1);
        assertThat(trustSummaryService.detail(member).trustLevel()).isEqualTo(1);
        assertThat(trustSummaryService.detail(member).noRecentSanction()).isFalse();
    }

    @Test
    @DisplayName("[TR-01][SN-10] 180일보다 1마이크로초 앞선 제재, 해제된 제재, 임시 정지는 단계에 영향이 없다")
    void oldLiftedAndTemporarySanctionsDoNotMatter() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        makeQualified(member);
        insertSanction(member, "WARNING", "EXPIRED", NOW.minus(RECENT_PERIOD).minusNanos(1_000));
        insertSanction(member, "SUSPEND_7D", "LIFTED", NOW.minus(Duration.ofDays(1)));
        insertSanction(member, "TEMPORARY_72H", "ACTIVE", NOW.minus(Duration.ofDays(1)));

        assertThat(trustSummaryService.summarize(member).trustLevel()).isEqualTo(2);
        assertThat(trustSummaryService.detail(member).noRecentSanction()).isTrue();
    }

    @Test
    @DisplayName("[TR-01][SN-10] 기간이 끝난 제재도 시작한 지 180일이 안 됐으면 단계 2에서 뺀다")
    void expiredSanctionStillCountsWithinPeriod() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        makeQualified(member);
        insertSanction(member, "SUSPEND_7D", "EXPIRED", NOW.minus(Duration.ofDays(30)));

        assertThat(trustSummaryService.summarize(member).trustLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-15] 대표 태그는 공개된 후기의 태그만 세어 많은 순서로 3개, 같은 횟수는 선언 순서로 고른다")
    void topTagsCountOnlyRevealedReviews() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());
        long basecampId = fixture.saveBasecamp("COMPLETED", LONG_AGO);
        fixture.insertMember(basecampId, member, "MEMBER", "ACTIVE");
        tagged(basecampId, fixture.leaderIdOf(basecampId), member, LONG_AGO, "LATE", "ON_TIME");
        tagged(basecampId, fixture.saveVerifiedMember(TestSequence.nickname()), member, LONG_AGO, "LATE");
        tagged(
                basecampId,
                fixture.saveVerifiedMember(TestSequence.nickname()),
                member,
                LONG_AGO,
                "ON_TIME",
                "CONSIDERATE",
                "NO_SHOW");
        // 숨긴 후기와 공개 전 후기의 태그는 세지 않는다.
        long hidden =
                tagged(basecampId, fixture.saveVerifiedMember(TestSequence.nickname()), member, LONG_AGO, "LITTERING");
        fixture.hide(hidden);
        long unrevealedBasecamp = fixture.saveBasecamp("COMPLETED", JUST_COMPLETED);
        for (int i = 0; i < 4; i++) {
            tagged(
                    unrevealedBasecamp,
                    fixture.saveVerifiedMember(TestSequence.nickname()),
                    member,
                    NOW,
                    "WELL_PREPARED");
        }

        List<String> topTags = trustSummaryService.detail(member).topTags();

        assertThat(topTags).containsExactly("ON_TIME", "LATE", "CONSIDERATE");
    }

    @Test
    @DisplayName("[F-15] 공개된 후기가 없으면 대표 태그는 빈 목록이다")
    void noTagsGivesEmptyList() {
        long member = fixture.saveVerifiedMember(TestSequence.nickname());

        assertThat(trustSummaryService.detail(member).topTags()).isEmpty();
    }

    private long tagged(long basecampId, long reviewerId, long revieweeId, Instant createdAt, String... tags) {
        long reviewId = fixture.insertReview(basecampId, reviewerId, revieweeId, true, createdAt);
        for (String tag : tags) {
            fixture.insertTag(reviewId, tag);
        }
        return reviewId;
    }

    // 임박 탈퇴 행마다 베이스캠프가 따로 필요하다. 같은 베이스캠프에는 한 회원의 행이 하나뿐이기 때문이다.
    private void addEarlyLeave(long member, Instant leftAt) {
        long basecampId = fixture.saveBasecamp("CONFIRMED", null);
        fixture.insertEarlyLeaver(basecampId, member, leftAt);
    }

    private void makeQualified(long member) {
        fixture.insertQualifiedRecord(member, LONG_AGO);
    }

    private void insertSanction(long memberId, String type, String status, Instant startsAt) {
        jdbc.update(
                "INSERT INTO sanction (member_id, type, level, reason, starts_at, status, created_at, updated_at)"
                        + " VALUES (?, ?, 1, '테스트', ?, ?, NOW(6), NOW(6))",
                memberId,
                type,
                LocalDateTime.ofInstant(startsAt, ZoneOffset.UTC),
                status);
    }
}
