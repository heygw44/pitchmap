package com.pitchmap.basecamp.domain;

import com.pitchmap.common.testsupport.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 상태별 베이스캠프를 운영 메서드로 만든다. 불변식 검증을 우회하지 않는다.
 *
 * <p>기본 구성은 캠프 리더({@link #LEADER_ID}), 승인된 멤버({@link #MEMBER_ID}), 대기 중인 신청자({@link #APPLICANT_ID})다.
 * 대기 신청은 확정·취소·완료 상태에서는 만료된 채로 남는다.
 */
public class BasecampBuilder {

    public static final long LEADER_ID = 1L;
    public static final long MEMBER_ID = 2L;
    public static final long APPLICANT_ID = 3L;
    public static final long OUTSIDER_ID = 9L;
    public static final Instant NOW = MutableClock.DEFAULT_INSTANT;
    public static final Instant COMPLETED_AT = NOW.plusSeconds(3600);
    public static final LocalDate DEFAULT_START_DATE = LocalDate.of(2026, 10, 20);

    private static final long APPLICATION_ID_OFFSET = 1000L;
    private static final long SPOT_ID = 10L;
    private static final int DEFAULT_CAPACITY = 4;
    private static final int DEFAULT_NIGHTS = 2;

    private int capacity = DEFAULT_CAPACITY;
    private LocalDate startDate = DEFAULT_START_DATE;

    public static BasecampBuilder aBasecamp() {
        return new BasecampBuilder();
    }

    /** 신청자 ID로 정해지는 신청 ID다. 저장하지 않는 단위 테스트에서 신청을 가리키려고 쓴다. */
    public static long applicationIdOf(long applicantId) {
        return APPLICATION_ID_OFFSET + applicantId;
    }

    public static BasecampDetails details(int capacity, LocalDate startDate) {
        return new BasecampDetails(
                "북한산 백패킹",
                "함께 가요",
                startDate,
                startDate.plusDays(DEFAULT_NIGHTS),
                Capacity.of(capacity),
                JoinCondition.none());
    }

    /** 호출하면 applicantId가 신청하고 캠프 리더가 바로 승인해 멤버로 만든다. */
    public static void join(Basecamp basecamp, long applicantId) {
        BasecampApplication application = apply(basecamp, applicantId);
        basecamp.approve(application.getId(), NOW);
    }

    /** 호출하면 신청을 만들고, 저장하지 않아 비어 있는 ID를 신청자 ID로 정한 값으로 채운다. */
    public static BasecampApplication apply(Basecamp basecamp, long applicantId) {
        BasecampApplication application = basecamp.apply(applicantId, "같이 가고 싶어요", NOW);
        ReflectionTestUtils.setField(application, "id", applicationIdOf(applicantId));
        return application;
    }

    public BasecampBuilder capacity(int capacity) {
        this.capacity = capacity;
        return this;
    }

    public BasecampBuilder startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    /** 호출하면 아무도 신청하지 않은 모집 중 베이스캠프를 만든다. 인원은 캠프 리더 한 명이다. */
    public Basecamp openOnly() {
        return Basecamp.open(LEADER_ID, SPOT_ID, details(capacity, startDate), NOW);
    }

    public Basecamp inState(BasecampStatus state) {
        Basecamp basecamp = openOnly();
        join(basecamp, MEMBER_ID);
        apply(basecamp, APPLICANT_ID);
        move(basecamp, state);
        return basecamp;
    }

    /** 호출하면 정원이 차서 자동 마감된 베이스캠프를 만든다. 정원은 3명이다. */
    public Basecamp autoClosed() {
        Basecamp basecamp = capacity(3).openOnly();
        join(basecamp, MEMBER_ID);
        join(basecamp, APPLICANT_ID);
        return basecamp;
    }

    private static void move(Basecamp basecamp, BasecampStatus state) {
        switch (state) {
            case RECRUITING -> {}
            case CLOSED -> basecamp.close(NOW);
            case CONFIRMED -> basecamp.confirm(NOW);
            case COMPLETED -> {
                basecamp.confirm(NOW);
                basecamp.complete(COMPLETED_AT);
            }
            case CANCELED -> basecamp.cancel(CancelReason.LEADER, NOW);
        }
    }
}
