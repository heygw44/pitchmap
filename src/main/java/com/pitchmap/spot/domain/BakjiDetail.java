package com.pitchmap.spot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 제보한 박지의 상세. 장소({@link Spot}) 한 건에 하나씩 붙고, 장소의 ID를 그대로 기본 키로 쓴다.
 *
 * <p>제보자는 회원 모듈의 엔티티라서 연관관계로 걸지 않고 ID로만 가리킨다. 제보자는 바뀌지 않는다. 그래서 이 엔티티는 제보자를 바꾸는 메서드를
 * 두지 않고, 고치거나 지울 수 있는 사람을 {@link #isReportedBy}로 가린다.
 */
@Entity
@Table(name = "bakji_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BakjiDetail {

    @Id
    @Column(name = "spot_id")
    private Long spotId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "spot_id")
    @Getter(AccessLevel.NONE)
    private Spot spot;

    @Column(name = "reporter_id")
    private long reporterId;

    private String description;

    @Column(name = "has_water")
    private boolean hasWater;

    @Column(name = "has_toilet")
    private boolean hasToilet;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_level")
    private BakjiSignalLevel signalLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "ground_type")
    private BakjiGroundType groundType;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private BakjiDetail(Spot spot, long reporterId, BakjiContent content, Instant now) {
        this.spot = spot;
        this.reporterId = reporterId;
        this.description = content.description();
        this.hasWater = content.hasWater();
        this.hasToilet = content.hasToilet();
        this.signalLevel = content.signalLevel();
        this.groundType = content.groundType();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 호출하면 spot에 붙는 박지 상세를 만든다. spot은 이미 저장돼 ID가 있어야 하고, 박지(BAKJI)여야 한다. 아니면
     * {@link IllegalArgumentException}을 던진다.
     */
    public static BakjiDetail of(Spot spot, long reporterId, BakjiContent content, Instant now) {
        if (spot == null || content == null || now == null) {
            throw new IllegalArgumentException("박지 상세를 만드는 데 필요한 값이 null입니다.");
        }
        if (spot.getId() == null) {
            throw new IllegalArgumentException("박지 상세는 저장된 장소에만 붙일 수 있습니다.");
        }
        if (spot.getType() != SpotType.BAKJI) {
            throw new IllegalArgumentException("박지가 아닌 장소에는 박지 상세를 붙일 수 없습니다.");
        }
        return new BakjiDetail(spot, reporterId, content, now);
    }

    /** memberId인 회원이 이 박지를 제보했으면 true다. */
    public boolean isReportedBy(long memberId) {
        return reporterId == memberId;
    }

    /** 호출하면 설명을 바꾼다. null이면 설명을 지운다. 길이가 {@value BakjiContent#DESCRIPTION_MAX_LENGTH}자를 넘으면 {@link IllegalArgumentException}을 던진다. */
    public void changeDescription(String description, Instant now) {
        BakjiContent.requireValidDescription(description);
        this.description = description;
        this.updatedAt = now;
    }

    /** 호출하면 물과 화장실 유무를 바꾼다. */
    public void changeFacilities(boolean hasWater, boolean hasToilet, Instant now) {
        this.hasWater = hasWater;
        this.hasToilet = hasToilet;
        this.updatedAt = now;
    }

    /** 호출하면 통신 상태를 바꾼다. null이면 지운다. */
    public void changeSignalLevel(BakjiSignalLevel signalLevel, Instant now) {
        this.signalLevel = signalLevel;
        this.updatedAt = now;
    }

    /** 호출하면 바닥 유형을 바꾼다. null이면 지운다. */
    public void changeGroundType(BakjiGroundType groundType, Instant now) {
        this.groundType = groundType;
        this.updatedAt = now;
    }
}
