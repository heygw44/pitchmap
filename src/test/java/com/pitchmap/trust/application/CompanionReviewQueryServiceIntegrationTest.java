package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.CompanionReviewTag;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CompanionReviewQueryServiceIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Duration PERIOD = Duration.ofDays(14);

    @Autowired
    private CompanionReviewQueryService queryService;

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
    @DisplayName("[F-15] 작성할 후기: 내가 ACTIVE인 완료 베이스캠프의 다른 ACTIVE 멤버만 대상이고, 이미 쓴 상대는 빠진다")
    void pendingListsUnwrittenActiveMembers() {
        // given
        long basecampId = fixture.saveBasecamp("COMPLETED", NOW.minus(Duration.ofDays(1)));
        long me = fixture.saveVerifiedMember("나야나");
        long written = fixture.saveVerifiedMember("쓴상대");
        long open = fixture.saveVerifiedMember("안쓴상대");
        long left = fixture.saveVerifiedMember("탈퇴자");
        long leaderId = fixture.leaderIdOf(basecampId);
        fixture.insertMember(basecampId, me, "MEMBER", "ACTIVE");
        fixture.insertMember(basecampId, written, "MEMBER", "ACTIVE");
        fixture.insertMember(basecampId, open, "MEMBER", "ACTIVE");
        fixture.insertMember(basecampId, left, "MEMBER", "LEFT");
        fixture.insertReview(basecampId, me, written, true, NOW);

        // when
        List<PendingCompanionReview> pending = queryService.pending(me);

        // then
        assertThat(pending).hasSize(1);
        PendingCompanionReview item = pending.get(0);
        assertThat(item.basecampId()).isEqualTo(basecampId);
        assertThat(item.basecampTitle()).isEqualTo("굴업도 주말 1박");
        assertThat(item.completedAt()).isEqualTo(NOW.minus(Duration.ofDays(1)));
        assertThat(item.deadline()).isEqualTo(NOW.minus(Duration.ofDays(1)).plus(PERIOD));
        assertThat(item.targets())
                .extracting(PendingCompanionReview.Target::memberId)
                .containsExactly(leaderId, open);
    }

    @Test
    @DisplayName("[F-15] 작성할 후기: 대상이 남지 않은 베이스캠프, 기한이 지난 베이스캠프, 완료되지 않은 베이스캠프, 내가 ACTIVE가 아닌 베이스캠프는 빠진다")
    void pendingExcludesBasecampsWithoutTargetOrWindow() {
        // given
        long me = fixture.saveVerifiedMember("나야나");
        long done = fixture.saveBasecamp("COMPLETED", NOW);
        fixture.insertMember(done, me, "MEMBER", "ACTIVE");
        fixture.insertReview(done, me, fixture.leaderIdOf(done), true, NOW);
        long expired = fixture.saveBasecamp("COMPLETED", NOW.minus(PERIOD).minusSeconds(1));
        fixture.insertMember(expired, me, "MEMBER", "ACTIVE");
        long notCompleted = fixture.saveBasecamp("CONFIRMED", null);
        fixture.insertMember(notCompleted, me, "MEMBER", "ACTIVE");
        long kicked = fixture.saveBasecamp("COMPLETED", NOW);
        fixture.insertMember(kicked, me, "MEMBER", "KICKED");

        // when
        List<PendingCompanionReview> pending = queryService.pending(me);

        // then
        assertThat(pending).isEmpty();
    }

    @Test
    @DisplayName("[F-15] 작성할 후기: 기한 정각인 베이스캠프는 포함하고, 마감이 빠른 순서로 정렬한다")
    void pendingIncludesDeadlineInstantAndOrdersByDeadline() {
        // given
        long me = fixture.saveVerifiedMember("나야나");
        long later = fixture.saveBasecamp("COMPLETED", NOW.minus(Duration.ofDays(1)));
        long atDeadline = fixture.saveBasecamp("COMPLETED", NOW.minus(PERIOD));
        long earliest = fixture.saveBasecamp("COMPLETED", NOW.minus(Duration.ofDays(10)));
        for (long basecampId : List.of(later, atDeadline, earliest)) {
            fixture.insertMember(basecampId, me, "MEMBER", "ACTIVE");
        }

        // when
        List<PendingCompanionReview> pending = queryService.pending(me);

        // then
        assertThat(pending).extracting(PendingCompanionReview::basecampId).containsExactly(atDeadline, earliest, later);
    }

    @Test
    @DisplayName("[F-15] 작성할 후기: 단계 0 회원은 TRUST_LEVEL_INSUFFICIENT다")
    void pendingRequiresTrustLevelOne() {
        long unverified = fixture.saveMember("미확인");

        assertThatThrownBy(() -> queryService.pending(unverified))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT));
    }

    @Test
    @DisplayName("[RV-03] 받은 후기: 상대만 썼고 기한 안이면 공개 전 항목이고, 내가 그 상대에게 쓰면 바로 공개된다")
    void receivedRevealsAfterIWrite() {
        // given
        BlindPair pair = blindPair(NOW);
        long reviewId = fixture.insertReview(pair.basecampId(), pair.other(), pair.me(), true, NOW);
        fixture.insertTag(reviewId, "ON_TIME");
        fixture.insertTag(reviewId, "CONSIDERATE");

        // when
        CompanionReviewPage<ReceivedCompanionReview> before = queryService.received(pair.me(), 0, 20);
        commandService.write(
                pair.me(), pair.basecampId(), new CompanionReviewWriteCommand(pair.other(), false, List.of(), null));
        CompanionReviewPage<ReceivedCompanionReview> after = queryService.received(pair.me(), 0, 20);

        // then
        assertThat(before.content()).hasSize(1);
        ReceivedCompanionReview sealed = before.content().get(0);
        assertThat(sealed.revealed()).isFalse();
        assertThat(sealed.basecampId()).isEqualTo(pair.basecampId());
        assertThat(sealed.reviewId()).isNull();
        assertThat(sealed.comment()).isNull();
        assertThat(sealed.tags()).isNull();
        assertThat(sealed.reviewerNickname()).isNull();

        ReceivedCompanionReview revealed = after.content().get(0);
        assertThat(revealed.revealed()).isTrue();
        assertThat(revealed.reviewId()).isEqualTo(reviewId);
        assertThat(revealed.reviewerId()).isEqualTo(pair.other());
        assertThat(revealed.reviewerNickname()).isEqualTo("상대");
        assertThat(revealed.basecampTitle()).isEqualTo("굴업도 주말 1박");
        assertThat(revealed.rejoinWanted()).isTrue();
        assertThat(revealed.tags()).containsExactly(CompanionReviewTag.ON_TIME, CompanionReviewTag.CONSIDERATE);
        assertThat(revealed.comment()).isEqualTo("코멘트 " + pair.other() + "→" + pair.me());
        assertThat(revealed.createdAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[RV-03] 받은 후기: 기한 정각까지는 공개 전이고, 기한이 지나면 내가 쓰지 않아도 공개된다")
    void receivedRevealsAfterDeadline() {
        // given
        BlindPair pair = blindPair(NOW);
        fixture.insertReview(pair.basecampId(), pair.other(), pair.me(), true, NOW);

        // when
        clock.setInstant(NOW.plus(PERIOD));
        boolean atDeadline =
                queryService.received(pair.me(), 0, 20).content().get(0).revealed();
        clock.setInstant(NOW.plus(PERIOD).plusSeconds(1));
        boolean afterDeadline =
                queryService.received(pair.me(), 0, 20).content().get(0).revealed();

        // then
        assertThat(atDeadline).isFalse();
        assertThat(afterDeadline).isTrue();
    }

    @Test
    @DisplayName("[RV-03] 받은 후기: 숨긴 후기는 공개 조건을 채워도 목록에서 빠지고, 최신순으로 페이지를 나눈다")
    void receivedExcludesHiddenAndPaginatesNewestFirst() {
        // given
        BlindPair pair = blindPair(NOW.minus(Duration.ofDays(20)));
        long second = fixture.saveVerifiedMember("둘째");
        long third = fixture.saveVerifiedMember("셋째");
        long hiddenAuthor = fixture.saveVerifiedMember("숨김");
        for (long member : List.of(second, third, hiddenAuthor)) {
            fixture.insertMember(pair.basecampId(), member, "MEMBER", "ACTIVE");
        }
        long oldest = fixture.insertReview(pair.basecampId(), pair.other(), pair.me(), true, NOW.minusSeconds(300));
        long middle = fixture.insertReview(pair.basecampId(), second, pair.me(), true, NOW.minusSeconds(200));
        long newest = fixture.insertReview(pair.basecampId(), third, pair.me(), true, NOW.minusSeconds(100));
        long hidden = fixture.insertReview(pair.basecampId(), hiddenAuthor, pair.me(), true, NOW);
        fixture.hide(hidden);

        // when
        CompanionReviewPage<ReceivedCompanionReview> first = queryService.received(pair.me(), 0, 2);
        CompanionReviewPage<ReceivedCompanionReview> next = queryService.received(pair.me(), 1, 2);

        // then
        assertThat(first.content())
                .extracting(ReceivedCompanionReview::reviewId)
                .containsExactly(newest, middle);
        assertThat(first.hasNext()).isTrue();
        assertThat(next.content()).extracting(ReceivedCompanionReview::reviewId).containsExactly(oldest);
        assertThat(next.hasNext()).isFalse();
        assertThat(next.page()).isEqualTo(1);
        assertThat(next.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("[RV-06] 회원이 받은 후기: 공개 전 후기와 숨긴 후기는 빠지고, 내용에 작성자가 없다")
    void forMemberFiltersUnrevealedAndHidden() {
        // given
        long basecampId = fixture.saveBasecamp("COMPLETED", NOW);
        long target = fixture.saveVerifiedMember("대상");
        long a = fixture.saveVerifiedMember("멤버가");
        long b = fixture.saveVerifiedMember("멤버나");
        long c = fixture.saveVerifiedMember("멤버다");
        for (long member : List.of(target, a, b, c)) {
            fixture.insertMember(basecampId, member, "MEMBER", "ACTIVE");
        }
        fixture.insertReview(basecampId, a, target, true, NOW.minusSeconds(30));
        long revealedByReverse = fixture.insertReview(basecampId, b, target, true, NOW.minusSeconds(20));
        fixture.insertReview(basecampId, target, b, true, NOW.minusSeconds(10));
        long hiddenReview = fixture.insertReview(basecampId, c, target, true, NOW.minusSeconds(5));
        fixture.insertReview(basecampId, target, c, true, NOW.minusSeconds(4));
        fixture.hide(hiddenReview);
        fixture.insertTag(revealedByReverse, "LEAVE_NO_TRACE");

        // when
        CompanionReviewPage<PublicCompanionReview> page = queryService.forMember(target, 0, 20);

        // then
        assertThat(page.content()).hasSize(1);
        PublicCompanionReview item = page.content().get(0);
        assertThat(item.tags()).containsExactly(CompanionReviewTag.LEAVE_NO_TRACE);
        assertThat(item.comment()).isEqualTo("코멘트 " + b + "→" + target);
        assertThat(item.createdAt()).isEqualTo(NOW.minusSeconds(20));
    }

    @Test
    @DisplayName("[RV-06] 회원이 받은 후기: 기한이 지나면 반대 방향 후기가 없어도 보이고, 기한 정각까지는 보이지 않는다")
    void forMemberShowsAfterDeadline() {
        // given
        BlindPair pair = blindPair(NOW);
        fixture.insertReview(pair.basecampId(), pair.other(), pair.me(), true, NOW);

        // when
        clock.setInstant(NOW.plus(PERIOD));
        int atDeadline = queryService.forMember(pair.me(), 0, 20).content().size();
        clock.setInstant(NOW.plus(PERIOD).plusSeconds(1));
        int afterDeadline = queryService.forMember(pair.me(), 0, 20).content().size();

        // then
        assertThat(atDeadline).isZero();
        assertThat(afterDeadline).isEqualTo(1);
    }

    @Test
    @DisplayName("[RV-06] 회원이 받은 후기: 없는 회원과 탈퇴한 회원은 NOT_FOUND다")
    void forMemberOfMissingOrWithdrawnIsNotFound() {
        // given
        long withdrawn = fixture.saveVerifiedMember("탈퇴함");
        jdbc.update("UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", withdrawn);

        // when, then
        for (long memberId : List.of(999_999L, withdrawn)) {
            assertThatThrownBy(() -> queryService.forMember(memberId, 0, 20))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        }
    }

    // 나와 상대가 ACTIVE 멤버인 완료 베이스캠프를 만든다.
    private BlindPair blindPair(Instant completedAt) {
        long basecampId = fixture.saveBasecamp("COMPLETED", completedAt);
        long me = fixture.saveVerifiedMember("나야나");
        long other = fixture.saveVerifiedMember("상대");
        fixture.insertMember(basecampId, me, "MEMBER", "ACTIVE");
        fixture.insertMember(basecampId, other, "MEMBER", "ACTIVE");
        return new BlindPair(basecampId, me, other);
    }

    private record BlindPair(long basecampId, long me, long other) {}
}
