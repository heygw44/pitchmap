package com.pitchmap.common.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트에서 시간을 직접 움직이는 시계. 움직이기 전까지는 멈춰 있다.
 *
 * <p>기본 시각은 한국 시간으로 한낮이라 날짜 경계가 테스트에 끼어들지 않는다.
 *
 * <p>{@link #withZone(ZoneId)}는 복사본이 아니라 같은 시간 원천을 공유하는 뷰를 돌려준다.
 * 서비스가 {@code clock.withZone(KST)}를 필드에 저장해 두더라도 이후의 시간 이동을 따라가야 하기 때문이다.
 */
public final class MutableClock extends Clock {

    public static final Instant DEFAULT_INSTANT = Instant.parse("2026-10-05T03:00:00Z");

    private final TimeSource source;
    private final ZoneId zone;

    private MutableClock(TimeSource source, ZoneId zone) {
        this.source = source;
        this.zone = zone;
    }

    public static MutableClock atDefaultInstant() {
        return at(DEFAULT_INSTANT);
    }

    public static MutableClock at(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return new MutableClock(new TimeSource(instant), ZoneOffset.UTC);
    }

    @Override
    public Instant instant() {
        return source.current.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        return new MutableClock(source, zone);
    }

    public void setInstant(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        source.current.set(instant);
    }

    public void advance(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        source.current.updateAndGet(current -> current.plus(duration));
    }

    /** 이 시계를 만들 때의 시각으로 되돌린다. 같은 시간 원천을 쓰는 모든 뷰에 적용된다. */
    public void reset() {
        source.current.set(source.initial);
    }

    @Override
    public String toString() {
        return "MutableClock[" + instant() + ", " + zone + "]";
    }

    private static final class TimeSource {

        private final Instant initial;
        private final AtomicReference<Instant> current;

        private TimeSource(Instant initial) {
            this.initial = initial;
            this.current = new AtomicReference<>(initial);
        }
    }
}
