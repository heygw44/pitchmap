package com.pitchmap.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KmaBaseTimesTest {

    private static Instant kst(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    }

    @Test
    @DisplayName("[F-09] 02시 10분 전이면 단기예보는 전날 23시 발표를 쓴다")
    void shortTermUsesPreviousDay23BeforeFirstPublishAvailable() {
        // when
        LocalDateTime base = KmaBaseTimes.shortTermBase(kst("2026-10-05T02:09:59"));

        // then
        assertThat(base).isEqualTo(LocalDateTime.parse("2026-10-04T23:00:00"));
    }

    @Test
    @DisplayName("[F-09] 발표 10분 뒤부터 그 발표를 단기예보 기준으로 쓴다")
    void shortTermUsesPublishFromTenMinutesAfter() {
        assertThat(KmaBaseTimes.shortTermBase(kst("2026-10-05T02:10:00")))
                .isEqualTo(LocalDateTime.parse("2026-10-05T02:00:00"));
        assertThat(KmaBaseTimes.shortTermBase(kst("2026-10-05T12:58:00")))
                .isEqualTo(LocalDateTime.parse("2026-10-05T11:00:00"));
        assertThat(KmaBaseTimes.shortTermBase(kst("2026-10-05T23:10:00")))
                .isEqualTo(LocalDateTime.parse("2026-10-05T23:00:00"));
        assertThat(KmaBaseTimes.shortTermBase(kst("2026-10-05T23:59:59")))
                .isEqualTo(LocalDateTime.parse("2026-10-05T23:00:00"));
    }

    @Test
    @DisplayName("[F-09] 06시 10분 전이면 중기예보는 전날 18시 발표를 쓴다")
    void midTermUsesPreviousDay18BeforeFirstPublishAvailable() {
        // when
        LocalDateTime base = KmaBaseTimes.midTermBase(kst("2026-10-05T06:09:59"));

        // then
        assertThat(base).isEqualTo(LocalDateTime.parse("2026-10-04T18:00:00"));
    }

    @Test
    @DisplayName("[F-09] 발표 10분 뒤부터 그 발표를 중기예보 기준으로 쓴다")
    void midTermUsesPublishFromTenMinutesAfter() {
        assertThat(KmaBaseTimes.midTermBase(kst("2026-10-05T06:10:00")))
                .isEqualTo(LocalDateTime.parse("2026-10-05T06:00:00"));
        assertThat(KmaBaseTimes.midTermBase(kst("2026-10-05T18:09:00")))
                .isEqualTo(LocalDateTime.parse("2026-10-05T06:00:00"));
        assertThat(KmaBaseTimes.midTermBase(kst("2026-10-05T18:10:00")))
                .isEqualTo(LocalDateTime.parse("2026-10-05T18:00:00"));
    }

    @Test
    @DisplayName("[F-09] 한국 시각 기준으로 계산한다. UTC 날짜가 달라도 한국 날짜를 따른다")
    void usesKoreanTimeZone() {
        // given: UTC로는 10월 4일 16:30이지만 한국 시각으로는 10월 5일 01:30이다.
        Instant now = Instant.parse("2026-10-04T16:30:00Z");

        // when
        LocalDateTime base = KmaBaseTimes.shortTermBase(now);

        // then
        assertThat(base).isEqualTo(LocalDateTime.parse("2026-10-04T23:00:00"));
    }
}
