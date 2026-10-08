package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.error.ErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.CompanionReviewTag;
import com.pitchmap.trust.domain.TrustErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CompanionReviewCommandServiceIntegrationTest {

    // 테스트 시계의 기본 시각에 베이스캠프가 막 완료된 것으로 둔다. 그래서 기한은 기본 시각에서 14일 뒤다.
    private static final Instant COMPLETED_AT = MutableClock.DEFAULT_INSTANT;
    private static final Duration PERIOD = Duration.ofDays(14);

    @Autowired
    private CompanionReviewCommandService commandService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private CompanionReviewFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[RV-01] 완료된 베이스캠프의 ACTIVE 멤버끼리 쓰면 후기와 태그가 저장되고, 캠프 리더도 쓸 수 있다")
    void writesBetweenActiveMembers() {
        // given
        long basecampId = fixture.saveBasecamp("COMPLETED", COMPLETED_AT);
        long leaderId = fixture.leaderIdOf(basecampId);
        long memberId = fixture.saveVerifiedMember("새벽능선");
        fixture.insertMember(basecampId, memberId, "MEMBER", "ACTIVE");

        // when
        long fromMember = commandService.write(
                memberId,
                basecampId,
                new CompanionReviewWriteCommand(
                        leaderId, true, List.of(CompanionReviewTag.ON_TIME, CompanionReviewTag.CONSIDERATE), "좋았다"));
        long fromLeader = commandService.write(
                leaderId, basecampId, new CompanionReviewWriteCommand(memberId, false, List.of(), null));

        // then
        assertThat(fixture.reviewCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT rejoin_wanted FROM companion_review WHERE id = ?", Boolean.class, fromMember))
                .isTrue();
        assertThat(jdbc.queryForList(
                        "SELECT tag FROM companion_review_tag WHERE review_id = ? ORDER BY tag",
                        String.class,
                        fromMember))
                .containsExactly("CONSIDERATE", "ON_TIME");
        assertThat(jdbc.queryForObject("SELECT comment FROM companion_review WHERE id = ?", String.class, fromMember))
                .isEqualTo("좋았다");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM companion_review_tag WHERE review_id = ?", Integer.class, fromLeader))
                .isZero();
    }

    @Test
    @DisplayName("[RV-04] 공백뿐인 코멘트는 null로 저장한다")
    void blankCommentIsStoredAsNull() {
        // given
        Pair pair = completedPair();

        // when
        long reviewId = commandService.write(
                pair.reviewerId(),
                pair.basecampId(),
                new CompanionReviewWriteCommand(pair.revieweeId(), true, List.of(), "   "));

        // then
        assertThat(jdbc.queryForObject("SELECT comment FROM companion_review WHERE id = ?", String.class, reviewId))
                .isNull();
    }

    @Test
    @DisplayName("[RV-04] 코멘트가 301자이면 INVALID_INPUT이다")
    void tooLongCommentIsInvalid() {
        // given
        Pair pair = completedPair();

        // when, then
        assertCode(
                () -> commandService.write(
                        pair.reviewerId(),
                        pair.basecampId(),
                        new CompanionReviewWriteCommand(pair.revieweeId(), true, List.of(), "가".repeat(301))),
                CommonErrorCode.INVALID_INPUT);
        assertThat(fixture.reviewCount()).isZero();
    }

    @Test
    @DisplayName("[F-15] 본인확인을 하지 않은 단계 0 회원은 TRUST_LEVEL_INSUFFICIENT이고, 베이스캠프가 없어도 이 오류가 먼저다")
    void levelZeroIsRejectedFirst() {
        // given
        long basecampId = fixture.saveBasecamp("COMPLETED", COMPLETED_AT);
        long unverified = fixture.saveMember("미확인");
        fixture.insertMember(basecampId, unverified, "MEMBER", "ACTIVE");
        long leaderId = fixture.leaderIdOf(basecampId);

        // when, then
        assertCode(
                () -> commandService.write(
                        unverified, basecampId, new CompanionReviewWriteCommand(leaderId, true, List.of(), null)),
                CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        assertCode(
                () -> commandService.write(
                        unverified, 999_999L, new CompanionReviewWriteCommand(leaderId, true, List.of(), null)),
                CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
    }

    @Test
    @DisplayName("[RV-01] 없는 베이스캠프는 NOT_FOUND다")
    void missingBasecampIsNotFound() {
        // given
        long reviewer = fixture.saveVerifiedMember("작성자");
        long reviewee = fixture.saveVerifiedMember("상대");

        // when, then
        assertCode(
                () -> commandService.write(
                        reviewer, 999_999L, new CompanionReviewWriteCommand(reviewee, true, List.of(), null)),
                CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("[RV-01] 완료되지 않은 베이스캠프에는 쓸 수 없다")
    void notCompletedBasecampIsNotEligible() {
        for (String status : List.of("RECRUITING", "CONFIRMED", "CANCELED")) {
            // given
            long basecampId = fixture.saveBasecamp(status, null);
            long leaderId = fixture.leaderIdOf(basecampId);
            long memberId = fixture.saveVerifiedMember("멤버" + status);
            fixture.insertMember(basecampId, memberId, "MEMBER", "ACTIVE");

            // when, then
            assertCode(
                    () -> commandService.write(
                            memberId, basecampId, new CompanionReviewWriteCommand(leaderId, true, List.of(), null)),
                    TrustErrorCode.COMPANION_REVIEW_NOT_ELIGIBLE);
        }
        assertThat(fixture.reviewCount()).isZero();
    }

    @Test
    @DisplayName("[RV-01] 탈퇴한 멤버와 강퇴된 멤버는 쓸 수도, 받을 수도 없다")
    void leftAndKickedMembersAreNotEligible() {
        // given
        long basecampId = fixture.saveBasecamp("COMPLETED", COMPLETED_AT);
        long leaderId = fixture.leaderIdOf(basecampId);
        long activeId = fixture.saveVerifiedMember("활동");
        long leftId = fixture.saveVerifiedMember("탈퇴");
        long kickedId = fixture.saveVerifiedMember("강퇴");
        fixture.insertMember(basecampId, activeId, "MEMBER", "ACTIVE");
        fixture.insertMember(basecampId, leftId, "MEMBER", "LEFT");
        fixture.insertMember(basecampId, kickedId, "MEMBER", "KICKED");

        // when, then
        for (long outsider : List.of(leftId, kickedId)) {
            assertCode(
                    () -> commandService.write(
                            outsider, basecampId, new CompanionReviewWriteCommand(leaderId, true, List.of(), null)),
                    TrustErrorCode.COMPANION_REVIEW_NOT_ELIGIBLE);
            assertCode(
                    () -> commandService.write(
                            activeId, basecampId, new CompanionReviewWriteCommand(outsider, true, List.of(), null)),
                    TrustErrorCode.COMPANION_REVIEW_NOT_ELIGIBLE);
        }
        assertThat(fixture.reviewCount()).isZero();
    }

    @Test
    @DisplayName("[RV-01] 베이스캠프 멤버가 아닌 회원과는 후기를 주고받을 수 없고, 자기 자신에게도 쓸 수 없다")
    void strangerAndSelfAreNotEligible() {
        // given
        Pair pair = completedPair();
        long stranger = fixture.saveVerifiedMember("외부인");

        // when, then
        assertCode(
                () -> commandService.write(
                        stranger,
                        pair.basecampId(),
                        new CompanionReviewWriteCommand(pair.revieweeId(), true, List.of(), null)),
                TrustErrorCode.COMPANION_REVIEW_NOT_ELIGIBLE);
        assertCode(
                () -> commandService.write(
                        pair.reviewerId(),
                        pair.basecampId(),
                        new CompanionReviewWriteCommand(stranger, true, List.of(), null)),
                TrustErrorCode.COMPANION_REVIEW_NOT_ELIGIBLE);
        assertCode(
                () -> commandService.write(
                        pair.reviewerId(),
                        pair.basecampId(),
                        new CompanionReviewWriteCommand(pair.reviewerId(), true, List.of(), null)),
                TrustErrorCode.COMPANION_REVIEW_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("[RV-02] 완료 후 정확히 14일이 되는 시각까지 쓸 수 있고, 1초라도 지나면 COMPANION_REVIEW_DEADLINE_PASSED다")
    void deadlineBoundaryIsInclusive() {
        // given
        Pair pair = completedPair();
        long other = fixture.saveVerifiedMember("다른 상대");
        fixture.insertMember(pair.basecampId(), other, "MEMBER", "ACTIVE");

        // when
        clock.setInstant(COMPLETED_AT.plus(PERIOD));
        long atDeadline = commandService.write(
                pair.reviewerId(),
                pair.basecampId(),
                new CompanionReviewWriteCommand(pair.revieweeId(), true, List.of(), null));
        clock.setInstant(COMPLETED_AT.plus(PERIOD).plusSeconds(1));

        // then
        assertThat(atDeadline).isPositive();
        assertCode(
                () -> commandService.write(
                        pair.reviewerId(),
                        pair.basecampId(),
                        new CompanionReviewWriteCommand(other, true, List.of(), null)),
                TrustErrorCode.COMPANION_REVIEW_DEADLINE_PASSED);
        assertThat(fixture.reviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[RV-01] 같은 상대에게 같은 베이스캠프에서 두 번 쓰면 COMPANION_REVIEW_DUPLICATED이고, 반대 방향이나 다른 상대에게는 쓸 수 있다")
    void duplicateIsRejectedButOtherDirectionsAreNot() {
        // given
        Pair pair = completedPair();
        long third = fixture.saveVerifiedMember("세 번째");
        fixture.insertMember(pair.basecampId(), third, "MEMBER", "ACTIVE");
        commandService.write(
                pair.reviewerId(),
                pair.basecampId(),
                new CompanionReviewWriteCommand(pair.revieweeId(), true, List.of(), null));

        // when, then
        assertCode(
                () -> commandService.write(
                        pair.reviewerId(),
                        pair.basecampId(),
                        new CompanionReviewWriteCommand(pair.revieweeId(), false, List.of(), null)),
                TrustErrorCode.COMPANION_REVIEW_DUPLICATED);
        commandService.write(
                pair.revieweeId(),
                pair.basecampId(),
                new CompanionReviewWriteCommand(pair.reviewerId(), true, List.of(), null));
        commandService.write(
                pair.reviewerId(), pair.basecampId(), new CompanionReviewWriteCommand(third, true, List.of(), null));
        assertThat(fixture.reviewCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("[RV-02] 기한이 지났으면 이미 쓴 상대에게도 COMPANION_REVIEW_DEADLINE_PASSED가 중복 오류보다 먼저다")
    void deadlineIsCheckedBeforeDuplicate() {
        // given
        Pair pair = completedPair();
        fixture.insertReview(pair.basecampId(), pair.reviewerId(), pair.revieweeId(), true, COMPLETED_AT);
        clock.setInstant(COMPLETED_AT.plus(PERIOD).plusSeconds(1));

        // when, then
        assertCode(
                () -> commandService.write(
                        pair.reviewerId(),
                        pair.basecampId(),
                        new CompanionReviewWriteCommand(pair.revieweeId(), true, List.of(), null)),
                TrustErrorCode.COMPANION_REVIEW_DEADLINE_PASSED);
    }

    private Pair completedPair() {
        long basecampId = fixture.saveBasecamp("COMPLETED", COMPLETED_AT);
        long leaderId = fixture.leaderIdOf(basecampId);
        long memberId = fixture.saveVerifiedMember("새벽능선");
        fixture.insertMember(basecampId, memberId, "MEMBER", "ACTIVE");
        return new Pair(basecampId, memberId, leaderId);
    }

    private record Pair(long basecampId, long reviewerId, long revieweeId) {}

    private static void assertCode(ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
