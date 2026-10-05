package com.pitchmap.spot.infra;

import com.pitchmap.spot.domain.GeoPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 좌표 순서 확인용 테스트 전용 엔티티. 운영 테이블 spot의 필수 컬럼만 매핑해서, JPA로 저장하고 읽은 좌표를 MySQL이 같은 위도·경도로 보는지 확인할 때
 * 쓴다.
 */
@Entity
@Table(name = "spot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpotLocationProbe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;

    private String name;

    @Getter(AccessLevel.NONE)
    private Point location;

    @Column(name = "weather_nx")
    private short weatherNx;

    @Column(name = "weather_ny")
    private short weatherNy;

    @Column(name = "park_warning")
    private boolean parkWarning;

    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public SpotLocationProbe(String name, GeoPoint location, Instant now) {
        this.type = "CAMPSITE";
        this.name = name;
        this.location = location.toPoint();
        this.weatherNx = 60;
        this.weatherNy = 127;
        this.parkWarning = false;
        this.status = "ACTIVE";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public GeoPoint getLocation() {
        return GeoPoint.from(location);
    }
}
