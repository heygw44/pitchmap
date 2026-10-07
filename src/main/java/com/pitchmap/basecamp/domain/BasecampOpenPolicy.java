package com.pitchmap.basecamp.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** 회원이 베이스캠프를 열 때 지켜야 하는 일정과 개수 제한이다. */
public final class BasecampOpenPolicy {

    /** 한 회원이 동시에 열어 둘 수 있는 베이스캠프의 최대 개수다. */
    public static final int MAX_OPEN_PER_LEADER = 3;

    /**
     * 개수 제한에 세는 상태다. 정원이 차서 자동 마감한 베이스캠프는 빈자리가 생기면 다시 모집 중이 된다.
     * 그래서 마감 상태도 세어야, 다시 열려도 한 회원의 모집 중 베이스캠프가 3개를 넘지 않는다.
     */
    public static final Set<BasecampStatus> OPEN_COUNTED_STATUSES =
            Set.of(BasecampStatus.RECRUITING, BasecampStatus.CLOSED);

    /** 출발일은 오늘로부터 이 일수 안이어야 한다. */
    public static final int MAX_DAYS_UNTIL_START = 60;

    public static final int MIN_NIGHTS = 1;
    public static final int MAX_NIGHTS = 3;

    private BasecampOpenPolicy() {}

    /**
     * 출발일이 내일부터 {@value #MAX_DAYS_UNTIL_START}일 뒤까지이고 박 수가 {@value #MIN_NIGHTS}~{@value #MAX_NIGHTS}박이 아니면
     * {@link BasecampErrorCode#BASECAMP_SCHEDULE_INVALID}로 거부한다. 날짜가 null이어도 거부한다.
     *
     * @param today 한국 날짜 기준 오늘
     */
    public static void requireSchedule(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (startDate == null || endDate == null) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID);
        }
        long daysUntilStart = ChronoUnit.DAYS.between(today, startDate);
        long nights = ChronoUnit.DAYS.between(startDate, endDate);
        boolean startInRange = daysUntilStart >= 1 && daysUntilStart <= MAX_DAYS_UNTIL_START;
        boolean nightsInRange = nights >= MIN_NIGHTS && nights <= MAX_NIGHTS;
        if (!startInRange || !nightsInRange) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID);
        }
    }

    /** 이미 열어 둔 베이스캠프가 {@value #MAX_OPEN_PER_LEADER}개 이상이면 {@link BasecampErrorCode#BASECAMP_OPEN_LIMIT}로 거부한다. */
    public static void requireUnderOpenLimit(long openCount) {
        if (openCount >= MAX_OPEN_PER_LEADER) {
            throw new BasecampException(BasecampErrorCode.BASECAMP_OPEN_LIMIT);
        }
    }
}
