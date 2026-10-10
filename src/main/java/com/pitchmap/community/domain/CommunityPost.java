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

    /** 호출하면 글을 DELETED로 바꾼다. 행은 남는다. 이미 DELETED이면 아무것도 하지 않는다. */
    public void delete(Instant now) {
        requireNow(now);
        if (status == CommunityPostStatus.DELETED) {
            return;
        }
        this.status = CommunityPostStatus.DELETED;
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
