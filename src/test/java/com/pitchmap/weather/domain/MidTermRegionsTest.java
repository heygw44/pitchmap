package com.pitchmap.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.weather.infra.MidTermRegionTable;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MidTermRegionsTest {

    private final MidTermRegions regions = new MidTermRegions(MidTermRegionTable.load());

    static Stream<Arguments> places() {
        return Stream.of(
                Arguments.of("서울시청", 37.5665, 126.9780, "11B10101", "11B00000"),
                Arguments.of("강릉", 37.7519, 128.8761, "11D20501", "11D20000"),
                Arguments.of("춘천", 37.8813, 127.7298, "11D10301", "11D10000"),
                Arguments.of("제주시", 33.4996, 126.5312, "11G00201", "11G00000"),
                Arguments.of("부산", 35.1796, 129.0756, "11H20201", "11H20000"),
                Arguments.of("대구", 35.8714, 128.6014, "11H10701", "11H10000"),
                Arguments.of("대전", 36.3504, 127.3845, "11C20401", "11C20000"),
                Arguments.of("청주", 36.6424, 127.4890, "11C10301", "11C10000"),
                Arguments.of("전주", 35.8242, 127.1480, "11F10201", "11F10000"),
                Arguments.of("광주광역시", 35.1595, 126.8526, "11F20501", "11F20000"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("places")
    @DisplayName("[F-09] 장소 좌표에서 가장 가까운 지점의 중기기온 구역과 소속 육상 구역을 고른다")
    void picksNearestRegion(String place, double lat, double lng, String taRegId, String landRegId) {
        // when
        MidTermRegion region = regions.nearest(lat, lng);

        // then
        assertThat(region.taRegId()).isEqualTo(taRegId);
        assertThat(region.landRegId()).isEqualTo(landRegId);
    }

    @Test
    @DisplayName("[F-09] 구역 표는 중기기온 구역 코드가 겹치지 않고 육상 구역 코드는 11로 시작하는 8자리다")
    void tableHasUniqueTaRegIdsAndValidLandRegIds() {
        // when
        var all = MidTermRegionTable.load();

        // then
        assertThat(all).extracting(MidTermRegion::taRegId).doesNotHaveDuplicates();
        assertThat(all).extracting(MidTermRegion::landRegId).allMatch(id -> id.matches("11[A-H]\\d0000"));
        assertThat(all).extracting(MidTermRegion::lat).allMatch(lat -> lat > 33 && lat < 39);
        assertThat(all).extracting(MidTermRegion::lng).allMatch(lng -> lng > 125 && lng < 130);
    }
}
