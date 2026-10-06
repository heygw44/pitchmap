package com.pitchmap.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원이 다녀온 장소에 남긴 후기와 평점. 한 회원은 같은 장소에 방문일마다 후기를 하나만 쓸 수 있다.
 *
 * <p>장소와 작성자는 다른 모듈의 엔티티이므로 연관관계로 걸지 않고 ID로만 가리킨다. 방문일은 쓴 뒤에 바꿀 수 없고, 평점과 내용만 고칠 수 있다.
 */
@Entity
@Table(name = "spot_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpotReview {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    /** 후기 내용의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int CONTENT_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spot_id")
    private long spotId;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "visited_date")
    private LocalDate visitedDate;

    // 컬럼이 TINYINT라서, Hibernate가 short를 기본 SMALLINT로 보고 스키마 검증에서 어긋나지 않게 JDBC 타입을 지정한다.
    @JdbcTypeCode(SqlTypes.TINYINT)
    private short rating;

    private String content;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private SpotReview(long spotId, long memberId, LocalDate visitedDate, int rating, String content, Instant now) {
        this.spotId = spotId;
        this.memberId = memberId;
        this.visitedDate = visitedDate;
        this.rating = (short) rating;
        this.content = content;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 호출하면 memberId인 회원이 spotId인 장소를 visitedDate에 다녀와서 쓴 후기를 만든다.
     *
     * <p>visitedDate나 now가 null이거나, 평점이 {@value #MIN_RATING}~{@value #MAX_RATING}이 아니거나, 내용이 비었거나 {@value
     * #CONTENT_MAX_LENGTH}자를 넘으면 {@link IllegalArgumentException}을 던진다. 방문일이 오늘 뒤인지는 시각을 아는 호출하는 쪽이 검사한다.
     */
    public static SpotReview write(
            long spotId, long memberId, LocalDate visitedDate, int rating, String content, Instant now) {
        if (visitedDate == null || now == null) {
            throw new IllegalArgumentException("후기를 만드는 데 필요한 값이 null입니다.");
        }
        requireValidBody(rating, content);
        return new SpotReview(spotId, memberId, visitedDate, rating, content, now);
    }

    /** 호출하면 평점과 내용을 바꾼다. 방문일은 바꾸지 않는다. 값이 규칙을 어기면 {@link IllegalArgumentException}을 던진다. */
    public void revise(int rating, String content, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("후기를 고친 시각이 null입니다.");
        }
        requireValidBody(rating, content);
        this.rating = (short) rating;
        this.content = content;
        this.updatedAt = now;
    }

    /** memberId인 회원이 쓴 후기이면 true다. */
    public boolean isWrittenBy(long memberId) {
        return this.memberId == memberId;
    }

    private static void requireValidBody(int rating, String content) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException("평점은 " + MIN_RATING + "~" + MAX_RATING + "이어야 합니다.");
        }
        if (content == null || content.isBlank() || content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("후기 내용은 공백뿐이 아닌 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
