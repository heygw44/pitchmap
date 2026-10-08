package com.pitchmap.spot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
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

    @Test
    @DisplayName("[F-07] 박지를 새 좌표로 옮기면 좌표, 기상청 격자, 경계 판정 결과, 수정 시각을 바꾸고 위도·경도 순서를 지킨다")
    void moveToReplacesLocationGridJudgementAndUpdatedAt() {
        // given: 서울시청에서 부산시청 쪽 좌표로 옮긴다. 위도와 경도를 뒤바꾸면 격자가 달라진다.
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        GeoPoint busan = new GeoPoint(35.1796, 129.0756);
        Instant movedAt = NOW.plusSeconds(60);
        ParkAreaJudgement judgement = ParkAreaJudgement.inside(PROTECTED_AREA_ID, movedAt);

        // when
        spot.moveTo(busan, judgement, movedAt);

        // then
        assertThat(spot.getLocation()).isEqualTo(busan);
        WeatherGrid expected = WeatherGrid.from(busan);
        assertThat(spot.getWeatherNx()).isEqualTo((short) expected.nx());
        assertThat(spot.getWeatherNy()).isEqualTo((short) expected.ny());
        assertThat(spot.getWeatherNx()).isNotEqualTo((short) 60);
        assertThat(spot.isParkWarning()).isTrue();
        assertThat(spot.getProtectedAreaId()).isEqualTo(PROTECTED_AREA_ID);
        assertThat(spot.getAreaCheckedAt()).isEqualTo(movedAt);
        assertThat(spot.getUpdatedAt()).isEqualTo(movedAt);
        assertThat(spot.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-07] 기상청 격자 밖 좌표로 옮기려 하면 거부하고 좌표를 그대로 둔다")
    void moveToRejectsLocationOutsideWeatherGrid() {
        // given
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        GeoPoint tokyo = new GeoPoint(35.6762, 139.6503);

        // when & then
        assertThatThrownBy(() -> spot.moveTo(tokyo, ParkAreaJudgement.outside(NOW), NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(spot.getLocation()).isEqualTo(SEOUL_CITY_HALL);
        assertThat(spot.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-07] 박지 이름을 바꾸면 수정 시각도 바뀌고, 빈 이름이나 101자 이름은 거부한다")
    void renameChangesNameAndRejectsInvalidName() {
        // given
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        Instant renamedAt = NOW.plusSeconds(5);

        // when
        spot.rename("계곡 옆 평지", renamedAt);

        // then
        assertThat(spot.getName()).isEqualTo("계곡 옆 평지");
        assertThat(spot.getUpdatedAt()).isEqualTo(renamedAt);
        assertThatThrownBy(() -> spot.rename(" ", renamedAt)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> spot.rename("가".repeat(101), renamedAt)).isInstanceOf(IllegalArgumentException.class);
        assertThat(spot.getName()).isEqualTo("계곡 옆 평지");
    }

    @Test
    @DisplayName("[F-07] 박지를 삭제하면 상태가 DELETED가 되고 ACTIVE 박지가 아니게 된다")
    void deleteMarksSpotDeleted() {
        // given
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        assertThat(spot.isActiveBakji()).isTrue();
        Instant deletedAt = NOW.plusSeconds(10);

        // when
        spot.delete(deletedAt);

        // then
        assertThat(spot.getStatus()).isEqualTo(SpotStatus.DELETED);
        assertThat(spot.getUpdatedAt()).isEqualTo(deletedAt);
        assertThat(spot.isActiveBakji()).isFalse();
    }

    @Test
    @DisplayName("[F-08] ACTIVE 박지를 검토 대기로 바꾸면 상태가 PENDING_REVIEW가 되고 수정 시각을 갱신하며 ACTIVE 박지가 아니게 된다")
    void markPendingReviewChangesActiveBakji() {
        // given
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        Instant reviewedAt = NOW.plusSeconds(10);

        // when
        spot.markPendingReview(reviewedAt);

        // then
        assertThat(spot.getStatus()).isEqualTo(SpotStatus.PENDING_REVIEW);
        assertThat(spot.getUpdatedAt()).isEqualTo(reviewedAt);
        assertThat(spot.isActiveBakji()).isFalse();
    }

    @Test
    @DisplayName("[F-08] ACTIVE가 아닌 박지를 검토 대기로 바꾸면 IllegalStateException을 던지고 상태는 그대로다")
    void markPendingReviewRejectsNonActiveSpot() {
        // given
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        spot.delete(NOW.plusSeconds(1));

        // when & then
        assertThatThrownBy(() -> spot.markPendingReview(NOW.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
        assertThat(spot.getStatus()).isEqualTo(SpotStatus.DELETED);
    }

    @Test
    @DisplayName("[F-21] ACTIVE나 PENDING_REVIEW 박지를 숨기면 상태가 HIDDEN이 되고 수정 시각을 갱신한다")
    void hideChangesActiveAndPendingReviewToHidden() {
        // given
        Spot active = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        Spot pending = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        pending.markPendingReview(NOW.plusSeconds(1));
        Instant hiddenAt = NOW.plusSeconds(10);

        // when
        active.hide(hiddenAt);
        pending.hide(hiddenAt);

        // then
        assertThat(active.getStatus()).isEqualTo(SpotStatus.HIDDEN);
        assertThat(active.getUpdatedAt()).isEqualTo(hiddenAt);
        assertThat(pending.getStatus()).isEqualTo(SpotStatus.HIDDEN);
        assertThat(pending.getUpdatedAt()).isEqualTo(hiddenAt);
    }

    @Test
    @DisplayName("[F-21] 이미 숨겼거나 삭제한 박지를 숨기면 SPOT_INVALID_STATE이고 상태와 수정 시각은 그대로다")
    void hideRejectsHiddenAndDeletedSpot() {
        // given
        Spot hidden = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        hidden.hide(NOW.plusSeconds(1));
        Spot deleted = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        deleted.delete(NOW.plusSeconds(1));

        // when & then
        for (Spot spot : new Spot[] {hidden, deleted}) {
            SpotStatus before = spot.getStatus();
            assertThatThrownBy(() -> spot.hide(NOW.plusSeconds(5)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.SPOT_INVALID_STATE));
            assertThat(spot.getStatus()).isEqualTo(before);
            assertThat(spot.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
        }
    }

    @Test
    @DisplayName("[F-21] 검토 대기나 숨긴 박지를 복구하면 상태가 ACTIVE가 되고 수정 시각을 갱신한다")
    void restoreChangesPendingReviewAndHiddenToActive() {
        // given
        Spot pending = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        pending.markPendingReview(NOW.plusSeconds(1));
        Spot hidden = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        hidden.hide(NOW.plusSeconds(1));
        Instant restoredAt = NOW.plusSeconds(10);

        // when
        pending.restore(restoredAt);
        hidden.restore(restoredAt);

        // then
        assertThat(pending.getStatus()).isEqualTo(SpotStatus.ACTIVE);
        assertThat(pending.getUpdatedAt()).isEqualTo(restoredAt);
        assertThat(hidden.getStatus()).isEqualTo(SpotStatus.ACTIVE);
        assertThat(hidden.getUpdatedAt()).isEqualTo(restoredAt);
    }

    @Test
    @DisplayName("[F-21] ACTIVE이거나 삭제한 박지를 복구하면 SPOT_INVALID_STATE이고 상태와 수정 시각은 그대로다")
    void restoreRejectsActiveAndDeletedSpot() {
        // given
        Spot active = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        Spot deleted = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        deleted.delete(NOW);

        // when & then
        for (Spot spot : new Spot[] {active, deleted}) {
            SpotStatus before = spot.getStatus();
            assertThatThrownBy(() -> spot.restore(NOW.plusSeconds(5)))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(SpotErrorCode.SPOT_INVALID_STATE));
            assertThat(spot.getStatus()).isEqualTo(before);
            assertThat(spot.getUpdatedAt()).isEqualTo(NOW);
        }
    }
}
