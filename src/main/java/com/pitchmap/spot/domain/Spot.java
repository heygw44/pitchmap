package com.pitchmap.spot.domain;

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
import org.locationtech.jts.geom.Point;

/**
 * 지도에 표시하는 장소 하나(야영장, 자연휴양림, 사용자가 제보한 박지).
 *
 * <p>서버는 박지를 제보하거나 고칠 때 이 엔티티로 장소 행을 쓴다. 하지만 공공데이터를 대량으로 넣거나 고치는 동기화와, 공원 경계를 다시 적재한 뒤
 * 모든 박지를 다시 판정하는 작업은 행을 한 건씩 엔티티로 읽지 않고 MyBatis SQL 한 번으로 갱신한다.
 *
 * <p>좌표 컬럼에는 {@link GeoPoint#toPoint()}가 만든 JTS 점을 저장한다. 이 점은 MySQL WKT 순서(위도, 경도)와 달리 x에 경도, y에 위도를
 * 담는다. 그래서 바깥으로는 JTS 점을 내보내지 않고 {@link #getLocation()}으로 {@link GeoPoint}만 돌려준다.
 *
 * <p>공원 경계 판정 결과는 세 컬럼에 함께 저장한다. 경고 여부, 좌표를 포함하는 경계의 ID, 판정한 시각이다. 공원 경계는 MyBatis로만 적재하고
 * 조회해서 엔티티가 없다. 그래서 이 엔티티는 경계를 연관관계로 걸지 않고 ID로만 가리킨다.
 */
@Entity
@Table(name = "spot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Spot {

    /** 장소 이름의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int NAME_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private SpotType type;

    private String name;

    @Getter(AccessLevel.NONE)
    private Point location;

    private String address;

    @Column(name = "weather_nx")
    private short weatherNx;

    @Column(name = "weather_ny")
    private short weatherNy;

    @Column(name = "park_warning")
    private boolean parkWarning;

    @Column(name = "protected_area_id")
    private Long protectedAreaId;

    @Column(name = "area_checked_at")
    private Instant areaCheckedAt;

    @Enumerated(EnumType.STRING)
    private SpotStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private Spot(SpotType type, String name, GeoPoint location, WeatherGrid weatherGrid, Instant now) {
        this.type = type;
        this.name = name;
        this.location = location.toPoint();
        this.weatherNx = (short) weatherGrid.nx();
        this.weatherNy = (short) weatherGrid.ny();
        this.status = SpotStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 사용자가 제보한 ACTIVE 상태의 박지를 만든다. 주소는 비워 두고, 기상청 단기예보 격자는 좌표로 계산해서 채운다.
     * 공원 경계 판정 결과는 호출하는 쪽이 같은 좌표로 미리 판정해서 넘긴다.
     *
     * <p>좌표가 기상청 격자 범위 밖이면 {@link WeatherGrid#from}이 던진 {@link IllegalArgumentException}을 그대로 던진다. 그런 좌표의
     * 박지는 날씨를 조회할 수 없어서 만들지 않는다. 값이 null이거나 이름이 비어 있으면 호출하는 쪽의 버그라서 같은 예외를 던진다.
     */
    public static Spot bakji(String name, GeoPoint location, ParkAreaJudgement judgement, Instant now) {
        if (name == null || location == null || judgement == null || now == null) {
            throw new IllegalArgumentException("박지를 만드는 데 필요한 값이 null입니다.");
        }
        requireValidName(name);
        Spot spot = new Spot(SpotType.BAKJI, name, location, WeatherGrid.from(location), now);
        spot.applyParkAreaJudgement(judgement);
        return spot;
    }

    /**
     * 호출하면 공원 경계 판정 결과(경고 여부, 경계 ID, 판정 시각)를 바꾼다.
     *
     * <p>수정 시각은 바꾸지 않는다. 판정은 제보 내용을 고친 것이 아니고, 판정한 시각은 따로 areaCheckedAt에 남기기 때문이다.
     */
    public void applyParkAreaJudgement(ParkAreaJudgement judgement) {
        if (judgement == null) {
            throw new IllegalArgumentException("공원 경계 판정 결과가 null입니다.");
        }
        this.parkWarning = judgement.parkWarning();
        this.protectedAreaId = judgement.protectedAreaId();
        this.areaCheckedAt = judgement.checkedAt();
    }

    /** 호출하면 이름을 바꾸고 수정 시각을 now로 갱신한다. 이름이 비어 있거나 {@value #NAME_MAX_LENGTH}자를 넘으면 {@link IllegalArgumentException}을 던진다. */
    public void rename(String name, Instant now) {
        requireValidName(name);
        this.name = name;
        this.updatedAt = now;
    }

    /**
     * 호출하면 좌표를 바꾸고, 기상청 격자를 새 좌표로 다시 계산하고, 공원 경계 판정 결과를 새 판정으로 바꾼다. 수정 시각은 now로 갱신한다.
     * 좌표가 옮겨졌으므로 판정은 호출하는 쪽이 새 좌표로 미리 해서 넘긴다.
     *
     * <p>새 좌표가 기상청 격자 범위 밖이면 {@link WeatherGrid#from}이 던진 {@link IllegalArgumentException}을 던지고 아무것도 바꾸지 않는다.
     */
    public void moveTo(GeoPoint newLocation, ParkAreaJudgement judgement, Instant now) {
        if (newLocation == null || judgement == null || now == null) {
            throw new IllegalArgumentException("장소를 옮기는 데 필요한 값이 null입니다.");
        }
        WeatherGrid grid = WeatherGrid.from(newLocation);
        this.location = newLocation.toPoint();
        this.weatherNx = (short) grid.nx();
        this.weatherNy = (short) grid.ny();
        applyParkAreaJudgement(judgement);
        this.updatedAt = now;
    }

    /** 호출하면 상태를 {@link SpotStatus#DELETED}로 바꾸고 수정 시각을 now로 갱신한다. 행은 지우지 않는다. */
    public void delete(Instant now) {
        this.status = SpotStatus.DELETED;
        this.updatedAt = now;
    }

    /**
     * 호출하면 상태를 {@link SpotStatus#PENDING_REVIEW}로 바꾸고 수정 시각을 now로 갱신한다. 신고가 쌓여 관리자 검토를 기다리게 하는 전이다.
     * 지금 상태가 ACTIVE가 아니면 호출하는 쪽의 버그라서 {@link IllegalStateException}을 던진다.
     */
    public void markPendingReview(Instant now) {
        if (status != SpotStatus.ACTIVE) {
            throw new IllegalStateException("지도에 보이는 장소만 검토 대기로 바꿀 수 있습니다. 현재 상태: " + status);
        }
        this.status = SpotStatus.PENDING_REVIEW;
        this.updatedAt = now;
    }

    /**
     * 호출하면 관리자가 장소를 숨긴 것으로 상태를 {@link SpotStatus#HIDDEN}으로 바꾸고 수정 시각을 now로 갱신한다. ACTIVE나
     * PENDING_REVIEW에서만 숨길 수 있고, 이미 숨겼거나 제보자가 지운 장소이면 {@link BusinessException}(SPOT_INVALID_STATE)을 던진다.
     */
    public void hide(Instant now) {
        if (status != SpotStatus.ACTIVE && status != SpotStatus.PENDING_REVIEW) {
            throw new BusinessException(SpotErrorCode.SPOT_INVALID_STATE);
        }
        this.status = SpotStatus.HIDDEN;
        this.updatedAt = now;
    }

    /**
     * 호출하면 관리자가 장소를 되살린 것으로 상태를 {@link SpotStatus#ACTIVE}로 바꾸고 수정 시각을 now로 갱신한다. PENDING_REVIEW나
     * HIDDEN에서만 되살릴 수 있고, 이미 ACTIVE이거나 제보자가 지운 장소이면 {@link BusinessException}(SPOT_INVALID_STATE)을 던진다.
     */
    public void restore(Instant now) {
        if (status != SpotStatus.PENDING_REVIEW && status != SpotStatus.HIDDEN) {
            throw new BusinessException(SpotErrorCode.SPOT_INVALID_STATE);
        }
        this.status = SpotStatus.ACTIVE;
        this.updatedAt = now;
    }

    /** 제보자가 지운 장소(DELETED)이면 true다. 지운 장소는 관리자도 다루지 못하고 없는 것처럼 취급한다. */
    public boolean isDeleted() {
        return status == SpotStatus.DELETED;
    }

    /** 공공데이터에서 적재한 장소(야영장, 자연휴양림)이면 true다. */
    public boolean isPublicData() {
        return type == SpotType.CAMPSITE || type == SpotType.FOREST;
    }

    /** 사용자가 제보한 박지이고 지도에 보이는 상태(ACTIVE)이면 true다. 제보자가 고치거나 지울 수 있는 박지인지 가릴 때 쓴다. */
    public boolean isActiveBakji() {
        return type == SpotType.BAKJI && status == SpotStatus.ACTIVE;
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("박지 이름이 비어 있습니다.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("장소 이름은 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    /** 호출하면 저장된 JTS 점(x는 경도, y는 위도)을 위도·경도 좌표로 바꿔 돌려준다. */
    public GeoPoint getLocation() {
        return GeoPoint.from(location);
    }
}
