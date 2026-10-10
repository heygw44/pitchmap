package com.pitchmap.community.domain;

import com.pitchmap.common.error.BusinessException;
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
 * 회원이 커뮤니티 게시판에 쓴 글. 작성자만 고치고 지울 수 있고, 지워도 행은 남긴다.
 *
 * <p>작성자와 연결한 장소는 다른 모듈의 엔티티이므로 연관관계로 걸지 않고 ID로만 가리킨다. 장소를 연결하지 않은 글은 {@code spotId}가
 * {@code null}이다. 연결할 장소가 ACTIVE인지는 장소 모듈이 아는 사실이라서, 호출하는 쪽이 확인한다.
 */
@Entity
@Table(name = "community_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost {

    /** 제목의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int TITLE_MAX_LENGTH = 100;

    /** 본문의 최대 길이다. */
    public static final int CONTENT_MAX_LENGTH = 10000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    @Enumerated(EnumType.STRING)
    private CommunityCategory category;

    private String title;

    private String content;

    @Column(name = "spot_id")
    private Long spotId;

    @Enumerated(EnumType.STRING)
    private CommunityPostStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private CommunityPost(
            long memberId, CommunityCategory category, String title, String content, Long spotId, Instant now) {
        this.memberId = memberId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.spotId = spotId;
        this.status = CommunityPostStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 호출하면 memberId인 회원이 쓴 글을 ACTIVE 상태로 만든다. spotId는 장소를 연결하지 않으면 {@code null}이다.
     *
     * <p>category나 now가 null이거나, 제목이 공백뿐이거나 {@value #TITLE_MAX_LENGTH}자를 넘거나, 본문이 공백뿐이거나 {@value
     * #CONTENT_MAX_LENGTH}자를 넘으면 {@link IllegalArgumentException}을 던진다.
     */
    public static CommunityPost write(
            long memberId, CommunityCategory category, String title, String content, Long spotId, Instant now) {
        if (category == null || now == null) {
            throw new IllegalArgumentException("글을 만드는 데 필요한 값이 null입니다.");
        }
        requireValidTitle(title);
        requireValidContent(content);
        return new CommunityPost(memberId, category, title, content, spotId, now);
    }

    /**
     * 호출하면 null이 아닌 값만 바꾸고 수정 시각을 now로 한다. 셋 다 null이면 아무것도 바꾸지 않는다.
     *
     * <p>값이 규칙을 어기면 {@link IllegalArgumentException}을 던진다. 장소 연결은 {@link #changeSpot}으로 바꾼다.
     */
    public void revise(CommunityCategory category, String title, String content, Instant now) {
        requireNow(now);
        if (category == null && title == null && content == null) {
            return;
        }
        if (title != null) {
            requireValidTitle(title);
        }
        if (content != null) {
            requireValidContent(content);
        }
        if (category != null) {
            this.category = category;
        }
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        this.updatedAt = now;
    }

    /** 호출하면 연결한 장소를 spotId로 바꾸고 수정 시각을 now로 한다. spotId가 null이면 연결을 끊는다. */
    public void changeSpot(Long spotId, Instant now) {
        requireNow(now);
        this.spotId = spotId;
        this.updatedAt = now;
    }

    /** 호출하면 수정 시각을 now로 한다. 글 필드는 그대로이고 붙은 이미지만 바뀐 수정에 쓴다. */
    public void markEdited(Instant now) {
        requireNow(now);
        this.updatedAt = now;
    }

    /** 호출하면 글을 DELETED로 바꾼다. 행은 남는다. 이미 DELETED이면 아무것도 하지 않는다. */
    public void delete(Instant now) {
        requireNow(now);
        if (status == CommunityPostStatus.DELETED) {
            return;
        }
        this.status = CommunityPostStatus.DELETED;
        this.updatedAt = now;
    }

    /**
     * 호출하면 상태를 {@link CommunityPostStatus#PENDING_REVIEW}로 바꾸고 수정 시각을 now로 갱신한다. 신고가 쌓여 관리자 검토를 기다리게 하는 전이다.
     * 지금 상태가 ACTIVE가 아니면 호출하는 쪽의 버그라서 {@link IllegalStateException}을 던진다.
     */
    public void markPendingReview(Instant now) {
        if (status != CommunityPostStatus.ACTIVE) {
            throw new IllegalStateException("보이는 글만 검토 대기로 바꿀 수 있습니다. 현재 상태: " + status);
        }
        this.status = CommunityPostStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    /**
     * 호출하면 관리자가 글을 숨긴 것으로 상태를 {@link CommunityPostStatus#HIDDEN}으로 바꾸고 수정 시각을 now로 갱신한다. ACTIVE나
     * PENDING_REVIEW에서만 숨길 수 있고, 이미 숨겼거나 작성자가 지운 글이면 {@link BusinessException}(COMMUNITY_INVALID_STATE)을 던진다.
     */
    public void hide(Instant now) {
        if (status != CommunityPostStatus.ACTIVE && status != CommunityPostStatus.PENDING_REVIEW) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_INVALID_STATE);
        }
        this.status = CommunityPostStatus.HIDDEN;
        this.updatedAt = now;
    }

    /**
     * 호출하면 관리자가 글을 되살린 것으로 상태를 {@link CommunityPostStatus#ACTIVE}로 바꾸고 수정 시각을 now로 갱신한다. PENDING_REVIEW나
     * HIDDEN에서만 되살릴 수 있고, 이미 ACTIVE이거나 작성자가 지운 글이면 {@link BusinessException}(COMMUNITY_INVALID_STATE)을 던진다.
     */
    public void restore(Instant now) {
        if (status != CommunityPostStatus.PENDING_REVIEW && status != CommunityPostStatus.HIDDEN) {
            throw new BusinessException(CommunityErrorCode.COMMUNITY_INVALID_STATE);
        }
        this.status = CommunityPostStatus.ACTIVE;
        this.updatedAt = now;
    }

    /** memberId인 회원이 쓴 글이면 true다. */
    public boolean isWrittenBy(long memberId) {
        return this.memberId == memberId;
    }

    /** 목록과 상세에 보이는 글이면 true다. */
    public boolean isActive() {
        return status == CommunityPostStatus.ACTIVE;
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("글을 고친 시각이 null입니다.");
        }
    }

    private static void requireValidTitle(String title) {
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("제목은 공백뿐이 아닌 " + TITLE_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    private static void requireValidContent(String content) {
        if (content == null || content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("본문은 공백뿐이 아닌 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
