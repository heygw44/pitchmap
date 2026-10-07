package com.pitchmap.basecamp.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BasecampOpenPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @ParameterizedTest(name = "오늘 + {0}일")
    @ValueSource(ints = {1, 30, 60})
    @DisplayName("[F-12][BC-03] 출발일이 내일부터 60일 뒤까지이면 통과한다")
    void startDateWithinRangeIsAccepted(int daysFromToday) {
        // given
        LocalDate start = TODAY.plusDays(daysFromToday);

        // when & then
        assertThatCode(() -> BasecampOpenPolicy.requireSchedule(start, start.plusDays(1), TODAY))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "오늘 + {0}일")
    @ValueSource(ints = {-1, 0, 61})
    @DisplayName("[F-12][BC-03] 출발일이 오늘이거나 지났거나 61일 뒤이면 BASECAMP_SCHEDULE_INVALID로 거부한다")
    void startDateOutOfRangeIsRejected(int daysFromToday) {
        // given
        LocalDate start = TODAY.plusDays(daysFromToday);

        // when & then
        assertThatThrownBy(() -> BasecampOpenPolicy.requireSchedule(start, start.plusDays(1), TODAY))
                .isInstanceOf(BasecampException.class)
                .hasMessage(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID.message());
    }

    @ParameterizedTest(name = "{0}박")
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("[F-12][BC-03] 1박부터 3박까지는 통과한다")
    void nightsWithinRangeAreAccepted(int nights) {
        // given
        LocalDate start = TODAY.plusDays(10);

        // when & then
        assertThatCode(() -> BasecampOpenPolicy.requireSchedule(start, start.plusDays(nights), TODAY))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}박")
    @ValueSource(ints = {-1, 0, 4})
    @DisplayName("[F-12][BC-03] 종료일이 출발일보다 이르거나 같거나 4박이 넘으면 BASECAMP_SCHEDULE_INVALID로 거부한다")
    void nightsOutOfRangeAreRejected(int nights) {
        // given
        LocalDate start = TODAY.plusDays(10);

        // when & then
        assertThatThrownBy(() -> BasecampOpenPolicy.requireSchedule(start, start.plusDays(nights), TODAY))
                .isInstanceOf(BasecampException.class)
                .hasMessage(BasecampErrorCode.BASECAMP_SCHEDULE_INVALID.message());
    }

    @Test
    @DisplayName("[F-12][BC-03] 출발일이나 종료일이 null이면 BASECAMP_SCHEDULE_INVALID로 거부한다")
    void nullDatesAreRejected() {
        // when & then
        assertThatThrownBy(() -> BasecampOpenPolicy.requireSchedule(null, TODAY.plusDays(3), TODAY))
                .isInstanceOf(BasecampException.class);
        assertThatThrownBy(() -> BasecampOpenPolicy.requireSchedule(TODAY.plusDays(2), null, TODAY))
                .isInstanceOf(BasecampException.class);
    }

    @ParameterizedTest(name = "열어 둔 {0}개")
    @ValueSource(longs = {0, 1, 2})
    @DisplayName("[F-12][BC-01] 열어 둔 베이스캠프가 3개 미만이면 더 열 수 있다")
    void belowLimitIsAccepted(long openCount) {
        // when & then
        assertThatCode(() -> BasecampOpenPolicy.requireUnderOpenLimit(openCount))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "열어 둔 {0}개")
    @ValueSource(longs = {3, 4})
    @DisplayName("[F-12][BC-01] 열어 둔 베이스캠프가 3개 이상이면 BASECAMP_OPEN_LIMIT로 거부한다")
    void atOrAboveLimitIsRejected(long openCount) {
        // when & then
        assertThatThrownBy(() -> BasecampOpenPolicy.requireUnderOpenLimit(openCount))
                .isInstanceOf(BasecampException.class)
                .hasMessage(BasecampErrorCode.BASECAMP_OPEN_LIMIT.message());
    }
}
