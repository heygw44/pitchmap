package com.pitchmap.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommunityCommentTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    @DisplayName("[F-29][CM-04] 댓글을 만들면 ACTIVE 상태로 값을 담고 작성·수정 시각은 만든 시각이다")
    void writeKeepsValuesAsActive() {
        // when
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "좋은 글이에요", NOW);

        // then
        assertThat(comment.getPostId()).isEqualTo(3L);
        assertThat(comment.getMemberId()).isEqualTo(7L);
        assertThat(comment.getParentId()).isNull();
        assertThat(comment.getContent()).isEqualTo("좋은 글이에요");
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.ACTIVE);
        assertThat(comment.isActive()).isTrue();
        assertThat(comment.getCreatedAt()).isEqualTo(NOW);
        assertThat(comment.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-29][CM-04] 부모 댓글 ID가 있으면 답글이고, 없으면 최상위 댓글이다")
    void isReplyDependsOnParentId() {
        // when
        CommunityComment top = CommunityComment.write(3L, 7L, null, "댓글", NOW);
        CommunityComment reply = CommunityComment.write(3L, 7L, 11L, "답글", NOW);

        // then
        assertThat(top.isReply()).isFalse();
        assertThat(reply.isReply()).isTrue();
        assertThat(reply.getParentId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("[F-29] 댓글 내용 1000자는 받고 1001자는 거부한다")
    void contentLengthBoundary() {
        // when
        CommunityComment atLimit = CommunityComment.write(3L, 7L, null, "가".repeat(1000), NOW);

        // then
        assertThat(atLimit.getContent()).hasSize(1000);
        assertThatThrownBy(() -> CommunityComment.write(3L, 7L, null, "가".repeat(1001), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", " ", "   \n\t"})
    @DisplayName("[F-29] 내용이 비었거나 공백뿐이면 댓글을 만들지도 고치지도 못한다")
    void blankContentIsRejected(String content) {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);

        // then
        assertThatThrownBy(() -> CommunityComment.write(3L, 7L, null, content, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> comment.revise(content, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(comment.getContent()).isEqualTo("댓글");
    }

    @Test
    @DisplayName("[F-29] 내용이 null이거나 시각이 null이면 거부한다")
    void nullArgumentsAreRejected() {
        // then
        assertThatThrownBy(() -> CommunityComment.write(3L, 7L, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommunityComment.write(3L, 7L, null, "댓글", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29] 댓글을 고치면 내용과 수정 시각만 바뀐다")
    void reviseChangesContentAndUpdatedAt() {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);

        // when
        comment.revise("고친 댓글", LATER);

        // then
        assertThat(comment.getContent()).isEqualTo("고친 댓글");
        assertThat(comment.getUpdatedAt()).isEqualTo(LATER);
        assertThat(comment.getCreatedAt()).isEqualTo(NOW);
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.ACTIVE);
    }

    @Test
    @DisplayName("[F-29][CM-03] 댓글을 지우면 DELETED가 되고 내용이 null로 지워진다")
    void deleteClearsContent() {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);

        // when
        comment.delete(LATER);

        // then
        assertThat(comment.getStatus()).isEqualTo(CommunityCommentStatus.DELETED);
        assertThat(comment.isActive()).isFalse();
        assertThat(comment.getContent()).isNull();
        assertThat(comment.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("[F-29][CM-03] 이미 지운 댓글을 다시 지워도 수정 시각이 바뀌지 않는다")
    void deleteTwiceKeepsFirstDeletionTime() {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);
        comment.delete(LATER);

        // when
        comment.delete(LATER.plusSeconds(60));

        // then
        assertThat(comment.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    @DisplayName("[F-29] 작성자 ID와 글 ID를 비교한다")
    void isWrittenByAndBelongsTo() {
        // given
        CommunityComment comment = CommunityComment.write(3L, 7L, null, "댓글", NOW);

        // then
        assertThat(comment.isWrittenBy(7L)).isTrue();
        assertThat(comment.isWrittenBy(8L)).isFalse();
        assertThat(comment.belongsTo(3L)).isTrue();
        assertThat(comment.belongsTo(4L)).isFalse();
    }
}
