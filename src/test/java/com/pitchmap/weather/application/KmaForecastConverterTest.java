package com.pitchmap.weather.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.weather.application.WeatherForecast.MidTermForecast;
import com.pitchmap.weather.application.WeatherForecast.ShortTermForecast;
import com.pitchmap.weather.infra.KmaApiException;
import com.pitchmap.weather.infra.KmaMidTermItem;
import com.pitchmap.weather.infra.KmaShortTermItem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class KmaForecastConverterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    // 2026-10-04 06:00 KST. 픽스처 첫 항목의 예보 시각이다.
    private static final Instant FIXTURE_START = Instant.parse("2026-10-03T21:00:00Z");

    @Test
    @DisplayName("[F-09] 단기예보 픽스처에서 같은 시각의 기온·강수확률·풍속을 한 항목으로 묶는다")
    void groupsTemperatureProbabilityAndWindByTime() {
        // given
        List<KmaShortTermItem> items = shortTermFixture();

        // when
        List<ShortTermForecast> result = KmaForecastConverter.toShortTerm(items, FIXTURE_START);

        // then
        assertThat(result).isNotEmpty();
        ShortTermForecast first = result.getFirst();
        assertThat(first.at()).isEqualTo(FIXTURE_START);
        assertThat(first.temperature()).isEqualTo(15);
        assertThat(first.windSpeed()).isEqualTo(2.1);
        assertThat(result).extracting(ShortTermForecast::at).isSorted().doesNotHaveDuplicates();
        assertThat(result).extracting(ShortTermForecast::temperature).doesNotContainNull();
        assertThat(result)
                .extracting(ShortTermForecast::precipitationProbability)
                .doesNotContainNull();
    }

    @Test
    @DisplayName("[F-09] 예보 시각은 한국 시각을 UTC 시각으로 바꾼다")
    void convertsKoreanTimeToUtc() {
        // given
        List<KmaShortTermItem> items = List.of(new KmaShortTermItem("TMP", "20261004", "0900", "3"));

        // when
        List<ShortTermForecast> result = KmaForecastConverter.toShortTerm(items, Instant.parse("2026-10-03T00:00:00Z"));

        // then
        assertThat(result).extracting(ShortTermForecast::at).containsExactly(Instant.parse("2026-10-04T00:00:00Z"));
    }

    @Test
    @DisplayName("[F-09] 현재 시각보다 앞선 예보 시각은 뺀다")
    void dropsTimesBeforeNow() {
        // given: 한국 시각 2026-10-04 12:00
        Instant now = Instant.parse("2026-10-04T03:00:00Z");

        // when
        List<ShortTermForecast> result = KmaForecastConverter.toShortTerm(shortTermFixture(), now);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result).extracting(ShortTermForecast::at).allMatch(at -> !at.isBefore(now));
        assertThat(result.getFirst().at()).isEqualTo(now);
    }

    @Test
    @DisplayName("[F-09] 기온·강수확률·풍속이 아닌 항목은 쓰지 않는다")
    void ignoresOtherCategories() {
        // given
        List<KmaShortTermItem> items = List.of(new KmaShortTermItem("SKY", "20261004", "0900", "1"));

        // when
        List<ShortTermForecast> result = KmaForecastConverter.toShortTerm(items, Instant.parse("2026-10-03T00:00:00Z"));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("[F-09][NFR-05] 기온이 숫자가 아니면 기상청 호출 실패와 같은 예외를 던진다")
    void throwsKmaApiExceptionWhenValueIsNotNumber() {
        // given
        List<KmaShortTermItem> items = List.of(new KmaShortTermItem("TMP", "20261004", "0900", "abc"));
        Instant now = Instant.parse("2026-10-03T00:00:00Z");

        // when & then
        assertThatThrownBy(() -> KmaForecastConverter.toShortTerm(items, now)).isInstanceOf(KmaApiException.class);
    }

    @Test
    @DisplayName("[F-09] 중기예보 픽스처에서 4~7일차는 오전·오후를 나누고 8~10일차는 오전·오후에 같은 값을 넣는다")
    void convertsMidTermFixture() {
        // given
        LocalDate tmFcDate = LocalDate.of(2026, 10, 4);

        // when
        List<MidTermForecast> result = KmaForecastConverter.toMidTerm(
                midTermFixture("mid-land-fcst.json"), midTermFixture("mid-ta.json"), tmFcDate, tmFcDate);

        // then
        assertThat(result).hasSize(7);
        assertThat(result.getFirst())
                .isEqualTo(new MidTermForecast(LocalDate.of(2026, 10, 8), 10, 25, "맑음", "맑음", 10, 10));
        MidTermForecast day8 = result.get(4);
        assertThat(day8.date()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(day8.amSky()).isEqualTo("구름많음").isEqualTo(day8.pmSky());
        assertThat(day8.amPrecipitationProbability()).isEqualTo(20).isEqualTo(day8.pmPrecipitationProbability());
        assertThat(day8.minTemperature()).isEqualTo(14);
        assertThat(day8.maxTemperature()).isEqualTo(25);
        assertThat(result.getLast().date()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(result.getLast().maxTemperature()).isEqualTo(21);
    }

    @Test
    @DisplayName("[F-09] 오늘 이전 날짜의 중기예보는 뺀다")
    void dropsMidTermDatesBeforeToday() {
        // given: 발표 후 6일이 지난 날
        LocalDate tmFcDate = LocalDate.of(2026, 10, 4);
        LocalDate today = LocalDate.of(2026, 10, 10);

        // when
        List<MidTermForecast> result = KmaForecastConverter.toMidTerm(
                midTermFixture("mid-land-fcst.json"), midTermFixture("mid-ta.json"), tmFcDate, today);

        // then
        assertThat(result).extracting(MidTermForecast::date).first().isEqualTo(today);
        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("[F-09] 18시 발표처럼 4일차 값이 없으면 그 날짜를 빼고 나머지만 돌려준다")
    void skipsDaysWithoutAnyValue() {
        // given
        Map<String, String> landFields = new HashMap<>();
        landFields.put("wf5Am", "흐림");
        landFields.put("wf5Pm", "비");
        landFields.put("rnSt5Am", "60");
        landFields.put("rnSt5Pm", "70");
        Optional<KmaMidTermItem> land = Optional.of(new KmaMidTermItem(landFields));
        LocalDate tmFcDate = LocalDate.of(2026, 10, 4);

        // when
        List<MidTermForecast> result = KmaForecastConverter.toMidTerm(land, Optional.empty(), tmFcDate, tmFcDate);

        // then
        assertThat(result)
                .containsExactly(new MidTermForecast(LocalDate.of(2026, 10, 9), null, null, "흐림", "비", 60, 70));
    }

    @Test
    @DisplayName("[F-09][NFR-05] 중기예보 값이 정수가 아니면 기상청 호출 실패와 같은 예외를 던진다")
    void throwsKmaApiExceptionWhenMidTermValueIsNotNumber() {
        // given
        Optional<KmaMidTermItem> ta = Optional.of(new KmaMidTermItem(Map.of("taMin4", "춥다")));
        LocalDate tmFcDate = LocalDate.of(2026, 10, 4);

        // when & then
        assertThatThrownBy(() -> KmaForecastConverter.toMidTerm(Optional.empty(), ta, tmFcDate, tmFcDate))
                .isInstanceOf(KmaApiException.class);
    }

    private static List<KmaShortTermItem> shortTermFixture() {
        List<KmaShortTermItem> items = new ArrayList<>();
        for (JsonNode item :
                readFixture("vilage-fcst.json").at("/response/body/items/item").values()) {
            items.add(new KmaShortTermItem(
                    item.path("category").asString(),
                    item.path("fcstDate").asString(),
                    item.path("fcstTime").asString(),
                    item.path("fcstValue").asString()));
        }
        return items;
    }

    private static Optional<KmaMidTermItem> midTermFixture(String fileName) {
        JsonNode item = readFixture(fileName).at("/response/body/items/item/0");
        Map<String, String> fields = new HashMap<>();
        item.properties().forEach(e -> fields.put(e.getKey(), e.getValue().asString()));
        return Optional.of(new KmaMidTermItem(fields));
    }

    private static JsonNode readFixture(String fileName) {
        try {
            return JSON.readTree(new ClassPathResource("fixtures/weather/kma/" + fileName).getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
