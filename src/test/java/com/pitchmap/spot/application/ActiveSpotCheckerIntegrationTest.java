package com.pitchmap.spot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.ParkAreaJudgement;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.infra.SpotJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ActiveSpotCheckerIntegrationTest {

    @Autowired
    private ActiveSpotChecker activeSpotChecker;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-12][BC-04] 공원 경계 경고가 붙은 박지이면 true, 경고가 없는 박지이면 false다")
    void warningBakjiIsTrueAndCleanBakjiIsFalse() {
        // given
        long areaId = insertProtectedArea();
        long warning = saveBakji(ParkAreaJudgement.inside(areaId, MutableClock.DEFAULT_INSTANT));
        long clean = saveBakji(ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT));

        // when & then
        assertThat(activeSpotChecker.isWarningBakji(warning)).isTrue();
        assertThat(activeSpotChecker.isWarningBakji(clean)).isFalse();
    }

    @Test
    @DisplayName("[F-12][BC-04] 야영장은 경고 값이 켜져 있어도 false다")
    void campsiteIsNeverWarningBakji() {
        // given
        long areaId = insertProtectedArea();
        long campsite = saveBakji(ParkAreaJudgement.inside(areaId, MutableClock.DEFAULT_INSTANT));
        jdbc.update("UPDATE spot SET type = 'CAMPSITE' WHERE id = ?", campsite);

        // when & then
        assertThat(activeSpotChecker.isWarningBakji(campsite)).isFalse();
    }

    @Test
    @DisplayName("[F-12] 없는 장소이거나 ACTIVE가 아닌 장소이면 NOT_FOUND로 거부한다")
    void missingOrHiddenSpotIsNotFound() {
        // given
        long areaId = insertProtectedArea();
        long hidden = saveBakji(ParkAreaJudgement.inside(areaId, MutableClock.DEFAULT_INSTANT));
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hidden);

        // when & then
        assertThatThrownBy(() -> activeSpotChecker.isWarningBakji(hidden))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CommonErrorCode.NOT_FOUND.message());
        assertThatThrownBy(() -> activeSpotChecker.isWarningBakji(hidden + 1000))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CommonErrorCode.NOT_FOUND.message());
    }

    private long saveBakji(ParkAreaJudgement judgement) {
        Spot spot = Spot.bakji("능선 끝 평지", new GeoPoint(37.25, 127.25), judgement, MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private long insertProtectedArea() {
        jdbc.update(
                "INSERT INTO protected_area (name, area_type, source, source_date, boundary, created_at, updated_at)"
                        + " VALUES ('북한산', 'NATIONAL_PARK', 'KDPA', '2026-01-01',"
                        + " ST_GeomFromText('MULTIPOLYGON(((127 37, 128 37, 128 38, 127 38, 127 37)))', 4326, 'axis-order=long-lat'),"
                        + " NOW(6), NOW(6))");
        return jdbc.queryForObject("SELECT id FROM protected_area WHERE name = '북한산'", Long.class);
    }
}
