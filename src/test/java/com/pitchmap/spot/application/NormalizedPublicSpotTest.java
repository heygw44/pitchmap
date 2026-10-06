package com.pitchmap.spot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.spot.infra.PublicSpotRow;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalizedPublicSpotTest {

    private static final double SEOUL_LATITUDE = 37.5665;
    private static final double SEOUL_LONGITUDE = 126.978;
    private static final int CAMPING_EMOJI_CODE_POINT = 0x1F3D5;

    @Test
    @DisplayName("[F-06] 문자열 앞뒤 공백을 지우고 빈 문자열은 null로 바꾼다")
    void stripsTextAndTurnsBlankIntoNull() {
        // given
        PublicSpotCommand command = new PublicSpotCommand(
                " 100 ",
                "  솔숲 야영장 ",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                "   ",
                " 일반야영장 ",
                null,
                "",
                " \t",
                null,
                null,
                null,
                null);

        // when
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(command);

        // then
        assertThat(spot.externalId()).isEqualTo("100");
        assertThat(spot.name()).isEqualTo("솔숲 야영장");
        assertThat(spot.address()).isNull();
        assertThat(spot.category()).isEqualTo("일반야영장");
        assertThat(spot.phone()).isNull();
        assertThat(spot.homepage()).isNull();
    }

    @Test
    @DisplayName("[F-06] 열 크기보다 긴 이름·주소·분류·전화번호는 열 크기에 맞춰 자른다")
    void truncatesTextLongerThanColumn() {
        // given
        PublicSpotCommand command = new PublicSpotCommand(
                "100",
                "가".repeat(NormalizedPublicSpot.MAX_NAME_LENGTH + 1),
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                "나".repeat(NormalizedPublicSpot.MAX_ADDRESS_LENGTH + 1),
                "다".repeat(NormalizedPublicSpot.MAX_CATEGORY_LENGTH + 1),
                null,
                "1".repeat(NormalizedPublicSpot.MAX_PHONE_LENGTH + 1),
                null,
                null,
                null,
                null,
                null);

        // when
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(command);

        // then
        assertThat(spot.name()).hasSize(NormalizedPublicSpot.MAX_NAME_LENGTH);
        assertThat(spot.address()).hasSize(NormalizedPublicSpot.MAX_ADDRESS_LENGTH);
        assertThat(spot.category()).hasSize(NormalizedPublicSpot.MAX_CATEGORY_LENGTH);
        assertThat(spot.phone()).hasSize(NormalizedPublicSpot.MAX_PHONE_LENGTH);
    }

    @Test
    @DisplayName("[F-06] 이모지가 든 이름은 Java 문자 수가 아니라 MySQL과 같이 코드 포인트 수로 잘라 이모지를 반으로 쪼개지 않는다")
    void truncatesByCodePointsSoSurrogatePairsStayWhole() {
        // given: 이모지 하나는 Java char 두 개지만 MySQL은 한 글자로 센다.
        String camping = Character.toString(CAMPING_EMOJI_CODE_POINT);
        String name = "가".repeat(NormalizedPublicSpot.MAX_NAME_LENGTH - 1) + camping + camping;
        PublicSpotCommand command = new PublicSpotCommand(
                "100", name, SEOUL_LATITUDE, SEOUL_LONGITUDE, null, null, null, null, null, null, null, null, null);

        // when
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(command);

        // then
        assertThat(spot.name().codePointCount(0, spot.name().length())).isEqualTo(NormalizedPublicSpot.MAX_NAME_LENGTH);
        assertThat(spot.name()).isEqualTo("가".repeat(NormalizedPublicSpot.MAX_NAME_LENGTH - 1) + camping);
    }

    @Test
    @DisplayName("[F-06] 열 크기보다 긴 홈페이지 주소는 자르지 않고 버린다")
    void dropsHomepageLongerThanColumn() {
        // given
        String homepage = "https://example.com/" + "a".repeat(NormalizedPublicSpot.MAX_HOMEPAGE_LENGTH);
        PublicSpotCommand command = new PublicSpotCommand(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                null,
                null,
                null,
                null,
                homepage,
                null,
                null,
                null,
                null);

        // when
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(command);

        // then
        assertThat(spot.homepage()).isNull();
    }

    @Test
    @DisplayName("[F-06] 시설 정보에서 값이 빈 항목을 빼고, 남은 항목이 없으면 null로 둔다")
    void dropsBlankFacilityValuesAndTurnsEmptyFacilitiesIntoNull() {
        // given
        Map<String, String> facilities = new LinkedHashMap<>();
        facilities.put("sbrsCl", " 전기,무선인터넷 ");
        facilities.put("toiletCo", "");
        facilities.put("animalCmgCl", "   ");
        PublicSpotCommand withFacilities = new PublicSpotCommand(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                null,
                null,
                facilities,
                null,
                null,
                null,
                null,
                null,
                null);
        PublicSpotCommand withBlankFacilities = new PublicSpotCommand(
                "101",
                "갈대 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                null,
                null,
                Map.of("toiletCo", ""),
                null,
                null,
                null,
                null,
                null,
                null);

        // when
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(withFacilities);
        NormalizedPublicSpot blankSpot = NormalizedPublicSpot.from(withBlankFacilities);

        // then
        assertThat(spot.facilities()).containsExactly(Map.entry("sbrsCl", "전기,무선인터넷"));
        assertThat(blankSpot.facilities()).isNull();
    }

    @Test
    @DisplayName("[F-06] 외부 ID나 이름이 비어 있으면 저장할 수 없는 값으로 거부한다")
    void rejectsBlankExternalIdOrName() {
        // given
        PublicSpotCommand blankExternalId = new PublicSpotCommand(
                " ", "솔숲 야영장", SEOUL_LATITUDE, SEOUL_LONGITUDE, null, null, null, null, null, null, null, null, null);
        PublicSpotCommand blankName = new PublicSpotCommand(
                "100", "", SEOUL_LATITUDE, SEOUL_LONGITUDE, null, null, null, null, null, null, null, null, null);

        // when & then
        assertThatThrownBy(() -> NormalizedPublicSpot.from(blankExternalId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NormalizedPublicSpot.from(blankName)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-06] 외부 ID가 열 크기보다 길면 자르지 않고 거부한다")
    void rejectsExternalIdLongerThanColumn() {
        // given
        String externalId = "1".repeat(NormalizedPublicSpot.MAX_EXTERNAL_ID_LENGTH + 1);
        PublicSpotCommand command = new PublicSpotCommand(
                externalId,
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        // when & then
        assertThatThrownBy(() -> NormalizedPublicSpot.from(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-06] 기상청 격자 밖 좌표는 저장할 수 없는 값으로 거부한다")
    void rejectsCoordinateOutsideWeatherGrid() {
        // given
        PublicSpotCommand command =
                new PublicSpotCommand("100", "적도 야영장", 0, 0, null, null, null, null, null, null, null, null, null);

        // when & then
        assertThatThrownBy(() -> NormalizedPublicSpot.from(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-06] MySQL이 경도를 읽을 때 생기는 부동소수 오차 안의 좌표는 바뀌지 않은 좌표로 본다")
    void treatsCoordinateWithinFloatingPointErrorAsUnchanged() {
        // given: 실제 MySQL에서 127.7298을 저장하고 ST_Longitude로 읽으면 127.72979999999998이 나왔다.
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(new PublicSpotCommand(
                "100", "솔숲 야영장", 37.8813, 127.7298, null, null, null, null, null, null, null, null, null));
        PublicSpotRow stored = storedRow(37.8813, 127.72979999999998);

        // when
        boolean differs = spot.differsInSpotColumns(stored);

        // then
        assertThat(differs).isFalse();
    }

    @Test
    @DisplayName("[F-06] 위도나 경도가 허용 오차보다 많이 다르면 바뀐 좌표로 본다")
    void treatsCoordinateBeyondToleranceAsChanged() {
        // given: 1e-6도는 허용 오차 1e-7도의 열 배이고 약 10cm다.
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(new PublicSpotCommand(
                "100", "솔숲 야영장", 37.8813, 127.7298, null, null, null, null, null, null, null, null, null));
        PublicSpotRow movedLatitude = storedRow(37.8813 + 1e-6, 127.7298);
        PublicSpotRow movedLongitude = storedRow(37.8813, 127.7298 + 1e-6);

        // when
        boolean latitudeDiffers = spot.differsInSpotColumns(movedLatitude);
        boolean longitudeDiffers = spot.differsInSpotColumns(movedLongitude);

        // then
        assertThat(latitudeDiffers).isTrue();
        assertThat(longitudeDiffers).isTrue();
    }

    @Test
    @DisplayName("[F-06] 운영 상태와 휴장 기간을 명령에서 그대로 가져오고, 휴장 시작일이 종료일보다 늦어도 거부하지 않는다")
    void keepsOperatingStatusAndClosedDatesAsGiven() {
        // given
        PublicSpotCommand command = campsiteCommand(
                PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
                LocalDate.parse("2027-03-15"),
                LocalDate.parse("2026-11-16"));

        // when
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(command);

        // then
        assertThat(spot.operatingStatus()).isEqualTo(PublicSpotOperatingStatus.TEMPORARILY_CLOSED);
        assertThat(spot.closedFrom()).isEqualTo(LocalDate.parse("2027-03-15"));
        assertThat(spot.closedUntil()).isEqualTo(LocalDate.parse("2026-11-16"));
        assertThat(spot.toColumns(null).operatingStatus()).isEqualTo("TEMPORARILY_CLOSED");
    }

    @Test
    @DisplayName("[F-06] 운영 상태와 휴장 기간이 저장된 값과 같으면 상세가 바뀌지 않은 것으로 본다")
    void sameOperatingStatusAndClosedDatesAreNotDifferent() {
        // given
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(campsiteCommand(
                PublicSpotOperatingStatus.TEMPORARILY_CLOSED,
                LocalDate.parse("2026-11-16"),
                LocalDate.parse("2027-03-15")));
        PublicSpotRow stored =
                detailRow("TEMPORARILY_CLOSED", LocalDate.parse("2026-11-16"), LocalDate.parse("2027-03-15"));
        NormalizedPublicSpot withoutStatus = NormalizedPublicSpot.from(campsiteCommand(null, null, null));

        // when
        boolean differs = spot.differsInDetailColumns(stored, null);
        boolean bothNullDiffers = withoutStatus.differsInDetailColumns(detailRow(null, null, null), null);

        // then
        assertThat(differs).isFalse();
        assertThat(bothNullDiffers).isFalse();
    }

    @Test
    @DisplayName("[F-06] 운영 상태나 휴장 기간이 저장된 값과 다르면, 한쪽이 null인 경우를 포함해 상세가 바뀐 것으로 본다")
    void differentOperatingStatusOrClosedDatesAreDifferent() {
        // given
        LocalDate from = LocalDate.parse("2026-11-16");
        LocalDate until = LocalDate.parse("2027-03-15");
        NormalizedPublicSpot spot =
                NormalizedPublicSpot.from(campsiteCommand(PublicSpotOperatingStatus.TEMPORARILY_CLOSED, from, until));
        NormalizedPublicSpot withoutStatus = NormalizedPublicSpot.from(campsiteCommand(null, null, null));

        // when & then
        assertThat(spot.differsInDetailColumns(detailRow("OPERATING", from, until), null))
                .isTrue();
        assertThat(spot.differsInDetailColumns(detailRow(null, from, until), null))
                .isTrue();
        assertThat(spot.differsInDetailColumns(detailRow("TEMPORARILY_CLOSED", from.plusDays(1), until), null))
                .isTrue();
        assertThat(spot.differsInDetailColumns(detailRow("TEMPORARILY_CLOSED", from, null), null))
                .isTrue();
        assertThat(spot.differsInDetailColumns(detailRow("TEMPORARILY_CLOSED", from, until.plusDays(1)), null))
                .isTrue();
        assertThat(withoutStatus.differsInDetailColumns(detailRow("OPERATING", null, null), null))
                .isTrue();
        assertThat(withoutStatus.differsInDetailColumns(detailRow(null, from, null), null))
                .isTrue();
    }

    @Test
    @DisplayName("[F-06] 기준일만 저장된 값과 다르면, 한쪽이 null인 경우를 포함해 상세가 바뀐 것으로 본다")
    void differentSourceDateIsDifferent() {
        // given
        LocalDate sourceDate = LocalDate.parse("2026-03-25");
        NormalizedPublicSpot spot = NormalizedPublicSpot.from(forestCommand(sourceDate));
        NormalizedPublicSpot withoutSourceDate = NormalizedPublicSpot.from(forestCommand(null));

        // when & then
        assertThat(spot.differsInDetailColumns(sourceDateRow(sourceDate), null)).isFalse();
        assertThat(withoutSourceDate.differsInDetailColumns(sourceDateRow(null), null))
                .isFalse();
        assertThat(spot.differsInDetailColumns(sourceDateRow(sourceDate.plusDays(1)), null))
                .isTrue();
        assertThat(spot.differsInDetailColumns(sourceDateRow(null), null)).isTrue();
        assertThat(withoutSourceDate.differsInDetailColumns(sourceDateRow(sourceDate), null))
                .isTrue();
    }

    private static PublicSpotCommand forestCommand(LocalDate sourceDate) {
        return new PublicSpotCommand(
                "100",
                "솔숲 휴양림",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                sourceDate);
    }

    private static PublicSpotRow sourceDateRow(LocalDate sourceDate) {
        return new PublicSpotRow(
                1L,
                "100",
                "솔숲 휴양림",
                null,
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                sourceDate,
                false);
    }

    private static PublicSpotCommand campsiteCommand(
            PublicSpotOperatingStatus operatingStatus, LocalDate closedFrom, LocalDate closedUntil) {
        return new PublicSpotCommand(
                "100",
                "솔숲 야영장",
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                null,
                null,
                null,
                null,
                null,
                operatingStatus,
                closedFrom,
                closedUntil,
                null);
    }

    private static PublicSpotRow detailRow(String operatingStatus, LocalDate closedFrom, LocalDate closedUntil) {
        return new PublicSpotRow(
                1L,
                "100",
                "솔숲 야영장",
                null,
                SEOUL_LATITUDE,
                SEOUL_LONGITUDE,
                null,
                null,
                null,
                null,
                operatingStatus,
                closedFrom,
                closedUntil,
                null,
                false);
    }

    private static PublicSpotRow storedRow(double latitude, double longitude) {
        return new PublicSpotRow(
                1L, "100", "솔숲 야영장", null, latitude, longitude, null, null, null, null, null, null, null, null, false);
    }
}
