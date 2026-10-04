package com.pitchmap.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class TestClockIntegrationTest {

    @Autowired
    Clock clock;

    @Autowired
    MutableClock mutableClock;

    @Test
    @DisplayName("Clock 주입 지점에는 운영 시계 대신 이동 가능한 시계가 들어가고 기본 시각에서 시작한다")
    void clockInjectionPointReceivesMutableClock() {
        // given
        Instant startedAt = clock.instant();

        // when
        // 주입받은 Clock과 MutableClock이 같은 객체라면 한쪽을 옮기면 다른 쪽도 움직인다.
        mutableClock.advance(Duration.ofMinutes(1));

        // then
        assertThat(startedAt).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(clock).isSameAs(mutableClock);
        assertThat(clock.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT.plus(Duration.ofMinutes(1)));
    }
}
