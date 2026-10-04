package com.pitchmap.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MutableClockTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("움직이기 전까지는 같은 시각을 돌려준다")
    void staysFixedUntilMoved() {
        // given
        MutableClock clock = MutableClock.atDefaultInstant();

        // when
        Instant first = clock.instant();
        Instant second = clock.instant();

        // then
        assertThat(first).isEqualTo(MutableClock.DEFAULT_INSTANT).isEqualTo(second);
    }

    @Test
    @DisplayName("advance를 거듭하면 시간이 누적된다")
    void advanceAccumulates() {
        // given
        MutableClock clock = MutableClock.atDefaultInstant();

        // when
        clock.advance(Duration.ofMinutes(10));
        clock.advance(Duration.ofMinutes(5));

        // then
        assertThat(clock.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("setInstant는 과거로도 시간을 옮길 수 있다")
    void setInstantJumpsBackwards() {
        // given
        MutableClock clock = MutableClock.atDefaultInstant();
        Instant past = MutableClock.DEFAULT_INSTANT.minus(Duration.ofDays(30));

        // when
        clock.setInstant(past);

        // then
        assertThat(clock.instant()).isEqualTo(past);
    }

    @Test
    @DisplayName("reset은 시계를 만들 때의 시각으로 되돌린다")
    void resetReturnsToCreationInstant() {
        // given
        Instant created = Instant.parse("2027-01-01T00:00:00Z");
        MutableClock clock = MutableClock.at(created);
        clock.advance(Duration.ofDays(3));
        clock.setInstant(Instant.parse("2020-01-01T00:00:00Z"));

        // when
        clock.reset();

        // then
        assertThat(clock.instant()).isEqualTo(created);
    }

    @Test
    @DisplayName("withZone 뷰는 원본의 이후 이동을 따라가고 새 시간대를 보고한다")
    void withZoneViewFollowsLaterAdvance() {
        // given
        MutableClock clock = MutableClock.atDefaultInstant();
        Clock view = clock.withZone(KST);

        // when
        clock.advance(Duration.ofHours(1));

        // then
        assertThat(view.getZone()).isEqualTo(KST);
        assertThat(clock.getZone()).isNotEqualTo(KST);
        assertThat(view.instant()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("withZone 뷰를 움직이면 원본과 reset도 함께 영향을 받는다")
    void viewAndOriginalShareTimeSource() {
        // given
        MutableClock clock = MutableClock.atDefaultInstant();
        MutableClock view = (MutableClock) clock.withZone(KST);

        // when
        view.advance(Duration.ofHours(2));
        Instant advanced = clock.instant();
        clock.reset();

        // then
        assertThat(advanced).isEqualTo(MutableClock.DEFAULT_INSTANT.plus(Duration.ofHours(2)));
        assertThat(view.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("여러 스레드가 동시에 advance해도 합계가 정확하다")
    void concurrentAdvanceSumsExactly() throws Exception {
        // given
        int threads = 8;
        int callsPerThread = 1000;
        MutableClock clock = MutableClock.atDefaultInstant();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // when
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> advanceRepeatedly(clock, start, callsPerThread)));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        // then
        Duration expected = Duration.ofMillis((long) threads * callsPerThread);
        assertThat(clock.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT.plus(expected));
    }

    @Test
    @DisplayName("기본 시각은 한국 시간으로 2026-10-05 한낮이고 12시간 뒤에는 다음 날이다")
    void defaultInstantIsMidDayInKst() {
        // given
        MutableClock clock = MutableClock.atDefaultInstant();
        Clock kstClock = clock.withZone(KST);

        // when
        LocalDate before = LocalDate.now(kstClock);
        clock.advance(Duration.ofHours(12));
        LocalDate after = LocalDate.now(kstClock);

        // then
        assertThat(before).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(after).isEqualTo(LocalDate.of(2026, 10, 6));
    }

    private static void advanceRepeatedly(MutableClock clock, CountDownLatch start, int calls) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        for (int i = 0; i < calls; i++) {
            clock.advance(Duration.ofMillis(1));
        }
    }
}
