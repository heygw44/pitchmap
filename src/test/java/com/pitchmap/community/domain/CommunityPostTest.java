package com.pitchmap.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CommunityPostTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    @DisplayName("[F-29][CM-02] 글을 만들면 ACTIVE 상태로 값을 담고 작성·수정 시각은 만든 시각이다")
    void writeKeepsValuesAsActive() {
        // when
        CommunityPost post = CommunityPost.write(7L, CommunityCategory.GEAR, "텐트 후기", "가볍다", 101L, NOW);

        // then
        assertThat(post.getMemberId()).isEqualTo(7L);
        assertThat(post.getCategory()).isEqualTo(CommunityCategory.GEAR);
        assertThat(post.getTitle()).isEqualTo("텐트 후기");
        assertThat(post.getContent()).isEqualTo("가볍다");
        assertThat(post.getSpotId()).isEqualTo(101L);
        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.ACTIVE);
        assertThat(post.isActive()).isTrue();
        assertThat(post.getCreatedAt()).isEqualTo(NOW);
        assertThat(post.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-29][CM-02] 장소를 연결하지 않고 글을 쓸 수 있다")
    void writeWithoutSpot() {
        // when
        CommunityPost post = CommunityPost.write(7L, CommunityCategory.FREE, "안녕", "반가워요", null, NOW);

        // then
        assertThat(post.getSpotId()).isNull();
    }

    @Test
    @DisplayName("[F-29][CM-02] 제목은 100자까지, 본문은 10000자까지 받고 한 글자만 넘어도 거부한다")
    void writeChecksLengthBoundaries() {
        // when
        CommunityPost atLimit =
                CommunityPost.write(7L, CommunityCategory.FREE, "가".repeat(100), "나".repeat(10000), null, NOW);

        // then
        assertThat(atLimit.getTitle()).hasSize(100);
        assertThat(atLimit.getContent()).hasSize(10000);
        assertThatThrownBy(() -> CommunityPost.write(7L, CommunityCategory.FREE, "가".repeat(101), "본문", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommunityPost.write(7L, CommunityCategory.FREE, "제목", "나".repeat(10001), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"", "   "})
    @DisplayName("[F-29][CM-02] 제목이나 본문이 비었거나 공백뿐이면 거부한다")
    void writeRejectsBlank(String blank) {
        assertThatThrownBy(() -> CommunityPost.write(7L, CommunityCategory.FREE, blank, "본문", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommunityPost.write(7L, CommunityCategory.FREE, "제목", blank, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29][CM-02] 카테고리나 시각이 null이면 글을 만들지 않는다")
    void writeRejectsNulls() {
        assertThatThrownBy(() -> CommunityPost.write(7L, null, "제목", "본문", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommunityPost.write(7L, CommunityCategory.FREE, "제목", "본문", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29][CM-03] 글을 고치면 null이 아닌 값만 바뀌고 수정 시각이 갱신되며 작성 시각은 그대로다")
    void reviseChangesOnlyGivenFields() {
        // given
        CommunityPost post = CommunityPost.write(7L, CommunityCategory.GEAR, "제목", "본문", 101L, NOW);

        // when
        post.revise(null, "새 제목", null, LATER);

        // then
        assertThat(post.getCategory()).isEqualTo(CommunityCategory.GEAR);
        assertThat(post.getTitle()).isEqualTo("새 제목");
        assertThat(post.getContent()).isEqualTo("본문");
        assertThat(post.getSpotId()).isEqualTo(101L);
        assertThat(post.getCreatedAt()).isEqualTo(NOW);
        assertThat(post.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("[F-29][CM-03] 고칠 값이 하나도 없으면 수정 시각도 바꾸지 않는다")
    void reviseWithNothingKeepsUpdatedAt() {
        // given
        CommunityPost post = CommunityPost.write(7L, CommunityCategory.GEAR, "제목", "본문", null, NOW);

        // when
        post.revise(null, null, null, LATER);

        // then
        assertThat(post.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-29][CM-02] 글을 고칠 때도 제목 100자·본문 10000자 제한을 지키고, 어기면 아무것도 바꾸지 않는다")
    void reviseChecksLengthBoundaries() {
        // given
        CommunityPost post = CommunityPost.write(7L, CommunityCategory.GEAR, "제목", "본문", null, NOW);

        // when
        post.revise(CommunityCategory.FREE, "가".repeat(100), "나".repeat(10000), LATER);

        // then
        assertThat(post.getTitle()).hasSize(100);
        assertThat(post.getContent()).hasSize(10000);
        assertThatThrownBy(() -> post.revise(CommunityCategory.GEAR, "가".repeat(101), null, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> post.revise(null, null, "나".repeat(10001), LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> post.revise(null, "  ", null, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(post.getCategory()).isEqualTo(CommunityCategory.FREE);
    }

    @Test
    @DisplayName("[F-29][CM-02] 장소 연결을 바꾸거나 null로 끊을 수 있다")
    void changeSpotConnectsAndDisconnects() {
        // given
        CommunityPost post = CommunityPost.write(7L, CommunityCategory.GEAR, "제목", "본문", 101L, NOW);

        // when
        post.changeSpot(202L, LATER);
        Long connected = post.getSpotId();
        post.changeSpot(null, LATER);

        // then
        assertThat(connected).isEqualTo(202L);
        assertThat(post.getSpotId()).isNull();
        assertThat(post.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("[F-29][CM-03] 글을 지우면 DELETED가 되어 더는 보이지 않고, 작성자 ID는 그대로 남는다")
    void deleteMarksDeleted() {
        // given
        CommunityPost post = CommunityPost.write(7L, CommunityCategory.GEAR, "제목", "본문", null, NOW);

        // when
        post.delete(LATER);

        // then
        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.DELETED);
        assertThat(post.isActive()).isFalse();
        assertThat(post.getMemberId()).isEqualTo(7L);
        assertThat(post.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("[F-29][CM-03] 작성자 ID가 같을 때만 isWrittenBy가 true다")
    void isWrittenByComparesAuthorId() {
        // given
        CommunityPost post = CommunityPost.write(7L, CommunityCategory.GEAR, "제목", "본문", null, NOW);

        // then
        assertThat(post.isWrittenBy(7L)).isTrue();
        assertThat(post.isWrittenBy(8L)).isFalse();
    }

    private CommunityPost postIn(CommunityPostStatus status) {
        CommunityPost post = CommunityPost.write(7L, CommunityCategory.FREE, "제목", "본문", null, NOW);
        switch (status) {
            case ACTIVE -> {}
            case PENDING_REVIEW -> post.markPendingReview(NOW);
            case HIDDEN -> post.hide(NOW);
            case DELETED -> post.delete(NOW);
        }
        return post;
    }

    @Test
    @DisplayName("[F-29][CM-07] ACTIVE 글을 검토 대기로 바꾸면 상태와 수정 시각이 바뀐다")
    void markPendingReviewFromActive() {
        // given
        CommunityPost post = postIn(CommunityPostStatus.ACTIVE);

        // when
        post.markPendingReview(LATER);

        // then
        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.PENDING_REVIEW);
        assertThat(post.getUpdatedAt()).isEqualTo(LATER);
        assertThat(post.isActive()).isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityPostStatus.class,
            names = {"PENDING_REVIEW", "HIDDEN", "DELETED"})
    @DisplayName("[F-29][CM-07] ACTIVE가 아닌 글을 검토 대기로 바꾸려 하면 IllegalStateException을 던진다")
    void markPendingReviewRejectsNonActive(CommunityPostStatus status) {
        // given
        CommunityPost post = postIn(status);

        // when // then
        assertThatThrownBy(() -> post.markPendingReview(LATER)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityPostStatus.class,
            names = {"ACTIVE", "PENDING_REVIEW"})
    @DisplayName("[F-29][CM-08] ACTIVE나 검토 대기 글을 숨기면 HIDDEN이 되고 수정 시각이 바뀐다")
    void hideFromActiveOrPending(CommunityPostStatus status) {
        // given
        CommunityPost post = postIn(status);

        // when
        post.hide(LATER);

        // then
        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.HIDDEN);
        assertThat(post.getUpdatedAt()).isEqualTo(LATER);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityPostStatus.class,
            names = {"HIDDEN", "DELETED"})
    @DisplayName("[F-29][CM-08] 이미 숨겼거나 작성자가 지운 글을 숨기면 COMMUNITY_INVALID_STATE로 거부한다")
    void hideRejectsHiddenOrDeleted(CommunityPostStatus status) {
        // given
        CommunityPost post = postIn(status);

        // when // then
        assertThatThrownBy(() -> post.hide(LATER))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommunityErrorCode.COMMUNITY_INVALID_STATE));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityPostStatus.class,
            names = {"PENDING_REVIEW", "HIDDEN"})
    @DisplayName("[F-29][CM-08] 검토 대기나 숨긴 글을 복구하면 ACTIVE가 되고 수정 시각이 바뀐다")
    void restoreFromPendingOrHidden(CommunityPostStatus status) {
        // given
        CommunityPost post = postIn(status);

        // when
        post.restore(LATER);

        // then
        assertThat(post.getStatus()).isEqualTo(CommunityPostStatus.ACTIVE);
        assertThat(post.getUpdatedAt()).isEqualTo(LATER);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @EnumSource(
            value = CommunityPostStatus.class,
            names = {"ACTIVE", "DELETED"})
    @DisplayName("[F-29][CM-08] 이미 ACTIVE이거나 작성자가 지운 글을 복구하면 COMMUNITY_INVALID_STATE로 거부한다")
    void restoreRejectsActiveOrDeleted(CommunityPostStatus status) {
        // given
        CommunityPost post = postIn(status);

        // when // then
        assertThatThrownBy(() -> post.restore(LATER))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommunityErrorCode.COMMUNITY_INVALID_STATE));
    }
}
