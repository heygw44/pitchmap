package com.pitchmap.spot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeatherGridTest {

    @Test
    @DisplayName("[F-09] 서울시청 좌표를 바꾸면 실제 기상청 단기예보 호출에 쓴 격자 (60, 127)이 나온다")
    void convertsSeoulCityHallToGridUsedInRealForecastCall() {
        // given
        // 개발자가 이 좌표와 격자 (60, 127)로 기상청 단기예보 API를 실제로 호출해 정상 응답을 받았다.
        GeoPoint seoulCityHall = new GeoPoint(37.5665, 126.9780);

        // when
        WeatherGrid grid = WeatherGrid.from(seoulCityHall);

        // then
        assertThat(grid).isEqualTo(new WeatherGrid(60, 127));
    }

    // 기대 격자는 기상청이 공공데이터포털 단기예보 조회서비스 참고문서로 배포하는 엑셀
    // "기상청41_단기예보 조회서비스_오픈API활용가이드_격자_위경도(2607).xlsx"에서 옮겨 왔다.
    // 위도·경도는 엑셀의 "위도(초/100)"·"경도(초/100)" 열이고, 기대 격자는 "격자 X"·"격자 Y" 열이다.
    // 내려받은 곳: https://www.data.go.kr/data/15084084/openapi.do (참고문서 "기상청41_단기예보 조회서비스_오픈API활용가이드_2609.zip")
    // 시·도 단위 행(서울, 부산, 강원, 제주) 네 곳과, 격자 번호가 가장 크거나 작은 행 네 곳을 골랐다. 뒤의 네 곳은 한국 영토의 끝이라서,
    // 격자 범위 검사가 실제 영토의 좌표를 거부하지 않는지도 함께 확인한다. 좌표가 0,0으로 비어 있는 이어도 행은 뺐다.
    // 엑셀에는 좌표와 격자 값이 서로 맞지 않는 행이 3,836행 중 43행 있다. 가이드에 실린 기상청 C 변환 코드를 엑셀 전체 행에 돌려도
    // 이 구현과 같은 행이 어긋나므로, 그 행들은 변환식이 아니라 엑셀 데이터의 문제다. 그래서 어긋나지 않는 행만 골랐다.
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "서울특별시, 37.5635694444444, 126.980008333333, 60, 127",
        "부산광역시, 35.1770194444444, 129.076952777777, 98, 76",
        "강원특별자치도, 37.8826916666666, 127.731975, 73, 134",
        "제주특별자치도, 33.4856944444444, 126.500333333333, 52, 38",
        "경상북도 울릉군 독도(X 최대), 37.2414386, 131.8648471, 144, 123",
        "제주특별자치도 서귀포시 대정읍(Y 최소), 33.2235722222222, 126.254175, 48, 32",
        "강원특별자치도 고성군 현내면(Y 최대), 38.4916444444444, 128.429844444444, 84, 147",
        "인천광역시 옹진군 대청면(X 최소), 37.8256, 124.7141, 21, 132",
    })
    @DisplayName("[F-09] 기상청 예시 좌표를 5km 격자로 바꾸면 기상청이 제공한 격자 값과 같다")
    void convertsKmaExampleCoordinatesToKmaGrid(
            String region, double latitude, double longitude, int expectedNx, int expectedNy) {
        // given
        GeoPoint point = new GeoPoint(latitude, longitude);

        // when
        WeatherGrid grid = WeatherGrid.from(point);

        // then
        assertThat(grid).as(region).isEqualTo(new WeatherGrid(expectedNx, expectedNy));
    }

    @ParameterizedTest(name = "nx={0}, ny={1}")
    @CsvSource({"1, 1", "149, 1", "1, 253", "149, 253"})
    @DisplayName("[F-09] 기상청 격자 범위의 네 모서리 번호는 거부하지 않고 그대로 격자 좌표가 된다")
    void keepsGridNumbersAtTheFourCornersOfKmaGrid(int nx, int ny) {
        // when
        WeatherGrid grid = new WeatherGrid(nx, ny);

        // then
        assertThat(grid).extracting(WeatherGrid::nx, WeatherGrid::ny).containsExactly(nx, ny);
    }

    @ParameterizedTest(name = "nx={0}, ny={1}")
    @CsvSource({"0, 127", "150, 127", "-1, 127", "60, 0", "60, 254", "60, -1"})
    @DisplayName("[F-09] 기상청 격자 범위에서 한 칸이라도 벗어난 번호는 격자 좌표로 만들 수 없다")
    void rejectsGridNumbersOutsideKmaRange(int nx, int ny) {
        // when & then
        assertThatThrownBy(() -> new WeatherGrid(nx, ny))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기상청 격자 범위");
    }

    // 한국에서 멀리 떨어진 좌표는 투영한 번호가 격자 범위를 벗어난다.
    // 남극점은 투영식의 값이 무한대나 NaN이 되는 점이다. 번호가 비정상 값으로 나와도 거부하는지 확인한다.
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "도쿄, 35.6762, 139.6503",
        "베이징, 39.9042, 116.4074",
        "위도·경도 0, 0, 0",
        "북극점, 90, 0",
        "남극점, -90, 126",
    })
    @DisplayName("[F-09] 기상청 격자 밖 좌표를 격자로 바꾸려 하면 거부한다")
    void rejectsCoordinatesOutsideKmaGrid(String place, double latitude, double longitude) {
        // given
        GeoPoint point = new GeoPoint(latitude, longitude);

        // when & then
        assertThatThrownBy(() -> WeatherGrid.from(point))
                .as(place)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("기상청 격자 범위");
    }
}
