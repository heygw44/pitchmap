package com.pitchmap.spot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.MutableClock;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BakjiDetailTest {

    private static final GeoPoint SEOUL_CITY_HALL = new GeoPoint(37.5665, 126.978);
    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    private static final long REPORTER_ID = 11L;

    private static final BakjiContent CONTENT =
            new BakjiContent("바람이 약한 평지", true, false, BakjiSignalLevel.WEAK, BakjiGroundType.GRASS);

    @Test
    @DisplayName("[F-07] 박지 상세를 만들면 제보자와 입력한 내용, 생성·수정 시각을 그대로 담는다")
    void ofKeepsReporterAndContent() {
        // when
        BakjiDetail detail = BakjiDetail.of(savedBakji(), REPORTER_ID, CONTENT, NOW);

        // then
        assertThat(detail.getReporterId()).isEqualTo(REPORTER_ID);
        assertThat(detail.getDescription()).isEqualTo("바람이 약한 평지");
        assertThat(detail.isHasWater()).isTrue();
        assertThat(detail.isHasToilet()).isFalse();
        assertThat(detail.getSignalLevel()).isEqualTo(BakjiSignalLevel.WEAK);
        assertThat(detail.getGroundType()).isEqualTo(BakjiGroundType.GRASS);
        assertThat(detail.getCreatedAt()).isEqualTo(NOW);
        assertThat(detail.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-07] 제보자 본인만 isReportedBy가 true다")
    void isReportedByMatchesOnlyReporter() {
        // given
        BakjiDetail detail = BakjiDetail.of(savedBakji(), REPORTER_ID, CONTENT, NOW);

        // when & then
        assertThat(detail.isReportedBy(REPORTER_ID)).isTrue();
        assertThat(detail.isReportedBy(REPORTER_ID + 1)).isFalse();
    }

    @Test
    @DisplayName("[F-07] 설명, 시설, 통신 상태, 바닥 유형을 고치면 값과 수정 시각이 바뀌고, 설명·통신 상태·바닥 유형은 null로 지울 수 있다")
    void changesFieldsAndClearsOptionalOnes() {
        // given
        BakjiDetail detail = BakjiDetail.of(savedBakji(), REPORTER_ID, CONTENT, NOW);
        Instant changedAt = NOW.plusSeconds(30);

        // when
        detail.changeDescription(null, changedAt);
        detail.changeFacilities(false, true, changedAt);
        detail.changeSignalLevel(null, changedAt);
        detail.changeGroundType(BakjiGroundType.DECK, changedAt);

        // then
        assertThat(detail.getDescription()).isNull();
        assertThat(detail.isHasWater()).isFalse();
        assertThat(detail.isHasToilet()).isTrue();
        assertThat(detail.getSignalLevel()).isNull();
        assertThat(detail.getGroundType()).isEqualTo(BakjiGroundType.DECK);
        assertThat(detail.getUpdatedAt()).isEqualTo(changedAt);
        assertThat(detail.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("[F-07] 설명이 2,000자를 넘으면 만들 때도 고칠 때도 거부한다")
    void rejectsDescriptionOverLimit() {
        // given
        String tooLong = "가".repeat(2001);
        BakjiDetail detail = BakjiDetail.of(savedBakji(), REPORTER_ID, CONTENT, NOW);

        // when & then
        assertThatThrownBy(() -> new BakjiContent(tooLong, true, true, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> detail.changeDescription(tooLong, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new BakjiContent("가".repeat(2000), true, true, null, null).description())
                .hasSize(2000);
    }

    @Test
    @DisplayName("[F-07] 저장하지 않은 장소에는 박지 상세를 붙일 수 없다")
    void rejectsUnsavedSpot() {
        // given
        Spot unsaved = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);

        // when & then
        assertThatThrownBy(() -> BakjiDetail.of(unsaved, REPORTER_ID, CONTENT, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 저장하면 JPA가 ID를 채운다. 단위 테스트는 스프링 없이 돌아서 리플렉션으로 ID를 넣는다.
    private static Spot savedBakji() {
        Spot spot = Spot.bakji("능선 끝 평지", SEOUL_CITY_HALL, ParkAreaJudgement.outside(NOW), NOW);
        try {
            Field id = Spot.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(spot, 205L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return spot;
    }
}
