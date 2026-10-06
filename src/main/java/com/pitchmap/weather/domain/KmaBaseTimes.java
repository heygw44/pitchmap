package com.pitchmap.weather.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * 기상청 예보의 발표 시각을 계산한다.
 *
 * <p>단기예보는 하루 8번(02, 05, 08, 11, 14, 17, 20, 23시), 중기예보는 하루 2번(06, 18시) 발표한다. 발표 시각 직후에는 자료가 아직
 * 올라오지 않았을 수 있어서, 발표 10분 뒤부터 그 발표를 조회한다. 그 전에는 직전 발표를 쓴다.
 */
public final class KmaBaseTimes {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration PUBLISH_DELAY = Duration.ofMinutes(10);
    private static final List<LocalTime> SHORT_TERM_TIMES = hours(2, 5, 8, 11, 14, 17, 20, 23);
    private static final List<LocalTime> MID_TERM_TIMES = hours(6, 18);

    private KmaBaseTimes() {}

    /** 지금 조회할 수 있는 가장 최근 단기예보 발표 시각(한국 시각). */
    public static LocalDateTime shortTermBase(Instant now) {
        return latestAvailable(now, SHORT_TERM_TIMES);
    }

    /** 지금 조회할 수 있는 가장 최근 중기예보 발표 시각(한국 시각). */
    public static LocalDateTime midTermBase(Instant now) {
        return latestAvailable(now, MID_TERM_TIMES);
    }

    private static LocalDateTime latestAvailable(Instant now, List<LocalTime> publishTimes) {
        LocalDateTime kst = LocalDateTime.ofInstant(now, KST);
        LocalDate date = kst.toLocalDate();
        for (int i = publishTimes.size() - 1; i >= 0; i--) {
            LocalDateTime publishedAt = date.atTime(publishTimes.get(i));
            if (!kst.isBefore(publishedAt.plus(PUBLISH_DELAY))) {
                return publishedAt;
            }
        }
        return date.minusDays(1).atTime(publishTimes.getLast());
    }

    private static List<LocalTime> hours(int... hours) {
        return Arrays.stream(hours).mapToObj(h -> LocalTime.of(h, 0)).toList();
    }
}
