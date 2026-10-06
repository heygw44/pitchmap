package com.pitchmap.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.weather.infra.KasiApiException;
import com.pitchmap.weather.infra.KasiRiseSetItem;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KasiSunTimesConverterTest {

    @Test
    @DisplayName("[F-09] 원천의 HHmm 문자열을 한국 시각의 LocalTime으로 바꾼다")
    void convertsHhmmToLocalTime() {
        // given
        KasiRiseSetItem item = new KasiRiseSetItem("20261004", "0630", "1811", "1838");

        // when
        SunTimes result = KasiSunTimesConverter.toSunTimes(item);

        // then
        assertThat(result)
                .isEqualTo(new SunTimes(
                        LocalDate.of(2026, 10, 4), LocalTime.of(6, 30), LocalTime.of(18, 11), LocalTime.of(18, 38)));
    }

    @Test
    @DisplayName("[F-09] 값 앞뒤 공백은 떼고 해석한다")
    void stripsSurroundingSpaces() {
        // given
        KasiRiseSetItem item = new KasiRiseSetItem("20261004", "0630  ", " 1811", "1838  ");

        // when
        SunTimes result = KasiSunTimesConverter.toSunTimes(item);

        // then
        assertThat(result.sunrise()).isEqualTo(LocalTime.of(6, 30));
        assertThat(result.sunset()).isEqualTo(LocalTime.of(18, 11));
    }

    @ParameterizedTest
    @ValueSource(strings = {"----", "", "630", "2460", "1260", "abcd", "06300"})
    @DisplayName("[F-09] 네 자리 시각이 아니거나 범위를 벗어난 값은 천문연 오류로 거부한다")
    void rejectsInvalidTime(String value) {
        // given
        KasiRiseSetItem item = new KasiRiseSetItem("20261004", value, "1811", "1838");

        // when & then
        assertThatThrownBy(() -> KasiSunTimesConverter.toSunTimes(item)).isInstanceOf(KasiApiException.class);
    }

    @Test
    @DisplayName("[F-09] 값이 없으면(null) 천문연 오류로 거부한다")
    void rejectsNullTime() {
        // given
        KasiRiseSetItem item = new KasiRiseSetItem("20261004", "0630", null, "1838");

        // when & then
        assertThatThrownBy(() -> KasiSunTimesConverter.toSunTimes(item)).isInstanceOf(KasiApiException.class);
    }

    @Test
    @DisplayName("[F-09] 날짜가 yyyyMMdd가 아니면 천문연 오류로 거부한다")
    void rejectsInvalidDate() {
        // given
        KasiRiseSetItem item = new KasiRiseSetItem("2026-10-04", "0630", "1811", "1838");

        // when & then
        assertThatThrownBy(() -> KasiSunTimesConverter.toSunTimes(item)).isInstanceOf(KasiApiException.class);
    }
}
