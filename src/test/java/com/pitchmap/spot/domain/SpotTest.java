package com.pitchmap.spot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpotTest {

    // 서울시청 좌표다. 개발자가 이 좌표의 기상청 격자 (60, 127)로 단기예보 API를 실제로 호출해 확인했다.
    private static final GeoPoint SEOUL_CITY_HALL = new GeoPoint(37.5665, 126.978);
    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final long PROTECTED_AREA_ID = 7L;

    @Test
    @DisplayName("[F-07] 공원 경계 안이라는 판정과 함께 박지를 만들면 경고, 경계 ID, 판정 시각을 그대로 담는다")
    void bakjiKeepsInsideJudgement() {
        // given
        Instant checkedAt = NOW.minusSeconds(1);
        ParkAreaJudgement judgement = ParkAreaJudgement.inside(PROTECTED_AREA_ID, checkedAt);

        // when
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, judgement, NOW);

        // then
        assertThat(spot.isParkWarning()).isTrue();
        assertThat(spot.getProtectedAreaId()).isEqualTo(PROTECTED_AREA_ID);
        assertThat(spot.getAreaCheckedAt()).isEqualTo(checkedAt);
    }

    @Test
    @DisplayName("[F-07] 박지를 만들면 종류는 BAKJI, 상태는 ACTIVE이고 기상청 격자를 좌표로 계산해 채운다")
    void bakjiIsActiveWithWeatherGridFromLocation() {
        // when
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);

        // then
        assertThat(spot.getType()).isEqualTo(SpotType.BAKJI);
        assertThat(spot.getStatus()).isEqualTo(SpotStatus.ACTIVE);
        assertThat(spot.getLocation()).isEqualTo(SEOUL_CITY_HALL);
        assertThat(spot.getAddress()).isNull();
        assertThat(spot.getWeatherNx()).isEqualTo((short) 60);
        assertThat(spot.getWeatherNy()).isEqualTo((short) 127);
        assertThat(spot.isParkWarning()).isFalse();
        assertThat(spot.getProtectedAreaId()).isNull();
        assertThat(spot.getCreatedAt()).isEqualTo(NOW);
        assertThat(spot.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-09] 기상청 격자 밖 좌표로는 박지를 만들 수 없다")
    void rejectsBakjiOutsideWeatherGrid() {
        // given: 도쿄는 한국에서 멀어서 기상청 격자 범위 밖이다.
        GeoPoint tokyo = new GeoPoint(35.6762, 139.6503);

        // when & then
        assertThatThrownBy(() -> Spot.bakji("도쿄 공원", tokyo, ParkAreaJudgement.outside(NOW), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기상청 격자 범위");
    }

    @Test
    @DisplayName("[F-07] 경계가 바뀌어 다시 판정하면 판정 결과만 바꾸고 수정 시각은 그대로 둔다")
    void reappliedJudgementReplacesResultWithoutTouchingUpdatedAt() {
        // given
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.inside(PROTECTED_AREA_ID, NOW), NOW);
        Instant rejudgedAt = NOW.plus(Duration.ofDays(30));

        // when
        spot.applyParkAreaJudgement(ParkAreaJudgement.outside(rejudgedAt));

        // then
        assertThat(spot.isParkWarning()).isFalse();
        assertThat(spot.getProtectedAreaId()).isNull();
        assertThat(spot.getAreaCheckedAt()).isEqualTo(rejudgedAt);
        assertThat(spot.getUpdatedAt()).isEqualTo(NOW);
    }
}
