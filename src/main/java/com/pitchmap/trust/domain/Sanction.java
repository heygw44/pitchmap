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
}
