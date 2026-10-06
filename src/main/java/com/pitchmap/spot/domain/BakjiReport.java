package com.pitchmap.spot.domain;

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
 * 회원이 잘못된 박지를 알리려고 남긴 신고. 한 회원은 같은 박지를 한 번만 신고할 수 있다.
 *
 * <p>박지와 신고한 회원은 다른 엔티티이므로 연관관계로 걸지 않고 ID로만 가리킨다. 신고는 추가만 하고 고치거나 지우지 않는다.
 */
@Entity
@Table(name = "bakji_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BakjiReport {

    /** 신고 내용의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int CONTENT_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spot_id")
    private long spotId;

    @Column(name = "reporter_id")
    private long reporterId;

    @Enumerated(EnumType.STRING)
    private BakjiReportReason reason;

    private String content;

    @Column(name = "created_at")
    private Instant createdAt;

    private BakjiReport(long spotId, long reporterId, BakjiReportReason reason, String content, Instant now) {
        this.spotId = spotId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.content = content;
        this.createdAt = now;
    }

    /**
     * 호출하면 reporterId인 회원이 spotId인 박지를 reason으로 신고한 기록을 만든다. content는 없어도 된다.
     *
     * <p>reason이나 now가 null이거나 content가 {@value #CONTENT_MAX_LENGTH}자를 넘으면 {@link IllegalArgumentException}을 던진다.
     */
    public static BakjiReport of(long spotId, long reporterId, BakjiReportReason reason, String content, Instant now) {
        if (reason == null || now == null) {
            throw new IllegalArgumentException("박지 신고를 만드는 데 필요한 값이 null입니다.");
        }
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("신고 내용은 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new BakjiReport(spotId, reporterId, reason, content, now);
    }
}
