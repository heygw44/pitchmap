package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampBuilder.COMPLETED_AT;
import static com.pitchmap.basecamp.domain.BasecampBuilder.MEMBER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.aBasecamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContactVisibilityTest {

    private static final Instant VISIBLE_UNTIL = COMPLETED_AT.plus(Duration.ofDays(7));

    @Test
    @DisplayName("[BC-23] 완료 후 7일 정각까지는 멤버에게 연락 수단이 보인다")
    void visibleExactlySevenDaysAfterCompletion() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.COMPLETED);

        assertThat(basecamp.canViewContact(MEMBER_ID, VISIBLE_UNTIL)).isTrue();
    }

    @Test
    @DisplayName("[BC-23] 완료 후 7일에서 1나노초만 지나도 연락 수단이 보이지 않는다")
    void hiddenJustAfterSevenDays() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.COMPLETED);

        assertThat(basecamp.canViewContact(MEMBER_ID, VISIBLE_UNTIL.plusNanos(1)))
                .isFalse();
    }

    @Test
    @DisplayName("[BC-23] 연락 수단을 등록하면 저장하고, 완료·취소된 뒤에는 등록할 수 없다")
    void registerContactOnlyBeforeFinish() {
        Basecamp confirmed = aBasecamp().inState(BasecampStatus.CONFIRMED);
        confirmed.registerContact("https://open.kakao.com/o/abc", COMPLETED_AT);

        assertThat(confirmed.getContactInfo()).isEqualTo("https://open.kakao.com/o/abc");
        BasecampAssertions.assertFailsWithInvalidState(() -> aBasecamp()
                .inState(BasecampStatus.COMPLETED)
                .registerContact("https://open.kakao.com/o/abc", COMPLETED_AT));
        BasecampAssertions.assertFailsWithInvalidState(() -> aBasecamp()
                .inState(BasecampStatus.CANCELED)
                .registerContact("https://open.kakao.com/o/abc", COMPLETED_AT));
    }

    @Test
    @DisplayName("[BC-23] 연락 수단이 비어 있거나 255자를 넘으면 거부한다")
    void registerContactRejectsInvalidText() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.CONFIRMED);

        assertThatThrownBy(() -> basecamp.registerContact(" ", COMPLETED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> basecamp.registerContact("a".repeat(256), COMPLETED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
