package com.pitchmap.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 커뮤니티 글에 회원이 다는 댓글. {@code parentId}가 있으면 그 댓글에 다는 답글이다. 작성자만 고치고 지울 수 있고, 지워도 행은 남긴다.
 *
 * <p>글과 작성자는 연관관계로 걸지 않고 ID로만 가리킨다. 답글은 한 단계만 허용한다. 부모가 같은 글의 ACTIVE 최상위 댓글인지는
 * 부모 행을 읽어야 알 수 있어서, 호출하는 쪽이 확인한다.
 */
@Entity
@Table(name = "community_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityComment {

    /** 내용의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int CONTENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id")
    private long postId;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "parent_id")
    private Long parentId;

    // 지운 댓글은 내용을 NULL로 지운다.
    private String content;

    @Enumerated(EnumType.STRING)
    private CommunityCommentStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private CommunityComment(long postId, long memberId, Long parentId, String content, Instant now) {
        this.postId = postId;
        this.memberId = memberId;
        this.parentId = parentId;
        this.content = content;
        this.status = CommunityCommentStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 호출하면 memberId인 회원이 postId인 글에 쓴 댓글을 ACTIVE 상태로 만든다. parentId는 최상위 댓글이면 {@code null}이다.
     *
     * <p>now가 null이거나, 내용이 공백뿐이거나 {@value #CONTENT_MAX_LENGTH}자를 넘으면 {@link IllegalArgumentException}을 던진다.
     */
    public static CommunityComment write(long postId, long memberId, Long parentId, String content, Instant now) {
        requireNow(now);
        requireValidContent(content);
        return new CommunityComment(postId, memberId, parentId, content, now);
    }

    /**
     * 호출하면 내용을 바꾸고 수정 시각을 now로 한다.
     *
     * <p>내용이 규칙을 어기면 {@link IllegalArgumentException}을 던진다.
     */
    public void revise(String content, Instant now) {
        requireNow(now);
        requireValidContent(content);
        this.content = content;
        this.updatedAt = now;
    }

    /**
     * 호출하면 댓글을 DELETED로 바꾸고 내용을 지운다. 행은 남는다. 답글이 달려 있는지와 상관없이 내용은 항상 지운다.
     * 이미 DELETED이면 아무것도 하지 않는다.
     */
    public void delete(Instant now) {
        requireNow(now);
        if (status == CommunityCommentStatus.DELETED) {
            return;
        }
        this.status = CommunityCommentStatus.DELETED;
        this.content = null;
        this.updatedAt = now;
    }

    /** memberId인 회원이 쓴 댓글이면 true다. */
    public boolean isWrittenBy(long memberId) {
        return this.memberId == memberId;
    }

    /** 내용이 보이는 댓글이면 true다. */
    public boolean isActive() {
        return status == CommunityCommentStatus.ACTIVE;
    }

    /** 다른 댓글에 다는 답글이면 true다. */
    public boolean isReply() {
        return parentId != null;
    }

    /** postId인 글에 달린 댓글이면 true다. */
    public boolean belongsTo(long postId) {
        return this.postId == postId;
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("댓글을 쓰거나 고친 시각이 null입니다.");
        }
    }

    private static void requireValidContent(String content) {
        if (content == null || content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("댓글은 공백뿐이 아닌 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
