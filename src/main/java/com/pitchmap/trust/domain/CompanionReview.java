package com.pitchmap.trust.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 완료된 베이스캠프의 멤버가 같은 베이스캠프의 다른 멤버에게 쓴 후기. 한 베이스캠프에서 같은 상대에게는 한 번만 쓸 수 있다.
 *
 * <p>쓴 뒤에는 바꾸거나 지우지 않으므로 수정 메서드가 없다. 베이스캠프와 회원은 다른 모듈의 엔티티라서 연관관계로 걸지 않고 ID로만 가리킨다.
 * 작성 자격과 기한은 서비스가 검사하고, 이 엔티티는 값의 형식만 지킨다.
 */
@Entity
@Table(name = "companion_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionReview {

    /** 코멘트의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int COMMENT_MAX_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "basecamp_id")
    private long basecampId;

    @Column(name = "reviewer_id")
    private long reviewerId;

    @Column(name = "reviewee_id")
    private long revieweeId;

    @Column(name = "rejoin_wanted")
    private boolean rejoinWanted;

    private String comment;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "companion_review_tag", joinColumns = @JoinColumn(name = "review_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", length = 30)
    private Set<CompanionReviewTag> tags = EnumSet.noneOf(CompanionReviewTag.class);

    private CompanionReview(
            long basecampId,
            long reviewerId,
            long revieweeId,
            boolean rejoinWanted,
            Collection<CompanionReviewTag> tags,
            String comment,
            Instant now) {
        this.basecampId = basecampId;
        this.reviewerId = reviewerId;
        this.revieweeId = revieweeId;
        this.rejoinWanted = rejoinWanted;
        this.tags = tags.isEmpty() ? EnumSet.noneOf(CompanionReviewTag.class) : EnumSet.copyOf(tags);
        this.comment = comment;
        this.createdAt = now;
    }

    /**
     * 호출하면 reviewerId인 회원이 basecampId인 베이스캠프에서 revieweeId인 회원에게 쓰는 후기를 만든다.
     *
     * <p>코멘트가 공백뿐이면 없는 것으로 저장한다. tags가 null이면 태그 없이 만든다. 같은 태그를 두 번 넘기면 한 번만 담는다.
     * 작성자와 상대가 같거나, 코멘트가 {@value #COMMENT_MAX_LENGTH}자를 넘거나, now가 null이면 {@link IllegalArgumentException}을 던진다.
     */
    public static CompanionReview write(
            long basecampId,
            long reviewerId,
            long revieweeId,
            boolean rejoinWanted,
            Collection<CompanionReviewTag> tags,
            String comment,
            Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("후기를 쓴 시각이 null입니다.");
        }
        if (reviewerId == revieweeId) {
            throw new IllegalArgumentException("자기 자신에게는 후기를 쓸 수 없습니다.");
        }
        if (comment != null && comment.length() > COMMENT_MAX_LENGTH) {
            throw new IllegalArgumentException("코멘트는 " + COMMENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        String normalizedComment = comment == null || comment.isBlank() ? null : comment;
        Collection<CompanionReviewTag> safeTags = tags == null ? Set.of() : tags;
        return new CompanionReview(basecampId, reviewerId, revieweeId, rejoinWanted, safeTags, normalizedComment, now);
    }
}
