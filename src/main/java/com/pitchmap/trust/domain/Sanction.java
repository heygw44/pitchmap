package com.pitchmap.trust.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원에게 내린 제재 한 건. 회원은 다른 모듈의 엔티티라서 ID로만 가리킨다.
 * 지금은 긴급 신고에 따른 임시 정지만 만든다.
 */
@Entity
@Table(name = "sanction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sanction {

    /** 임시 정지 기간이다. 관리자가 신고를 확인하기 전까지 대상 회원의 이용을 막는다. */
    public static final Duration TEMPORARY_SUSPENSION = Duration.ofHours(72);

    /** 확정 제재 사유의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int REASON_MAX_LENGTH = 500;

    static final String TEMPORARY_REASON = "성희롱·위협 신고가 접수되어 관리자가 확인하기 전까지 이용을 임시로 정지합니다.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    @Enumerated(EnumType.STRING)
    private SanctionType type;

    private Byte level;

    @Column(name = "report_id")
    private Long reportId;

    private String reason;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    private SanctionStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "lifted_by")
    private Long liftedBy;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private Sanction(long memberId, long reportId, Instant startsAt) {
        this.memberId = memberId;
        this.type = SanctionType.TEMPORARY_72H;
        this.reportId = reportId;
        this.reason = TEMPORARY_REASON;
        this.startsAt = startsAt;
        this.endsAt = startsAt.plus(TEMPORARY_SUSPENSION);
        this.status = SanctionStatus.ACTIVE;
        this.createdAt = startsAt;
        this.updatedAt = startsAt;
    }

    /**
     * 호출하면 reportId인 신고를 근거로 memberId인 회원을 startsAt부터 72시간 정지하는 제재를 만든다.
     * 제재 단계는 없고(NULL), 관리자가 아니라 서버가 내리므로 만든 사람도 없다. 상태는 적용 중이다.
     */
    public static Sanction temporary(long memberId, long reportId, Instant startsAt) {
        if (startsAt == null) {
            throw new IllegalArgumentException("정지를 시작하는 시각이 null입니다.");
        }
        return new Sanction(memberId, reportId, startsAt);
    }

    private Sanction(
            long memberId,
            Long reportId,
            SanctionType type,
            int level,
            String reason,
            Instant startsAt,
            long createdBy) {
        this.memberId = memberId;
        this.type = type;
        this.level = (byte) level;
        this.reportId = reportId;
        this.reason = reason;
        this.startsAt = startsAt;
        this.endsAt = endsAtOf(type, startsAt);
        this.status = SanctionStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = startsAt;
        this.updatedAt = startsAt;
    }

    /**
     * 호출하면 관리자 createdBy가 memberId인 회원에게 startsAt부터 적용하는 확정 제재를 만든다. 상태는 적용 중이다.
     * 7일·30일 정지는 startsAt부터 그 기간 뒤에 끝나고, 경고와 영구 정지는 끝나는 시각이 없다(NULL).
     * reportId는 근거 신고이고, 신고 없이 내리는 제재이면 null이다.
     *
     * <p>임시 정지 종류이거나, 단계가 종류와 맞지 않거나, 사유가 비었거나 {@value #REASON_MAX_LENGTH}자를 넘거나, 시작 시각이 null이면
     * {@link IllegalArgumentException}을 던진다. 다음 단계가 맞는지는 {@link SanctionPolicy}가 따로 판단한다.
     */
    public static Sanction confirm(
            long memberId,
            Long reportId,
            SanctionType type,
            int level,
            String reason,
            Instant startsAt,
            long createdBy) {
        if (startsAt == null) {
            throw new IllegalArgumentException("제재를 시작하는 시각이 null입니다.");
        }
        if (type == null || type == SanctionType.TEMPORARY_72H) {
            throw new IllegalArgumentException("확정 제재의 종류가 올바르지 않습니다. type=" + type);
        }
        if (level != SanctionPolicy.levelOf(type)) {
            throw new IllegalArgumentException("제재 단계가 종류와 맞지 않습니다. type=" + type + ", level=" + level);
        }
        if (reason == null || reason.isBlank() || reason.length() > REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("제재 사유는 공백뿐이 아닌 " + REASON_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new Sanction(memberId, reportId, type, level, reason, startsAt, createdBy);
    }

    private static Instant endsAtOf(SanctionType type, Instant startsAt) {
        return switch (type) {
            case SUSPEND_7D -> startsAt.plus(Duration.ofDays(7));
            case SUSPEND_30D -> startsAt.plus(Duration.ofDays(30));
            case TEMPORARY_72H -> startsAt.plus(TEMPORARY_SUSPENSION);
            case WARNING, PERMANENT -> null;
        };
    }
}
