package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampAssertions.assertFailsWith;
import static com.pitchmap.basecamp.domain.BasecampAssertions.assertFailsWithInvalidState;
import static com.pitchmap.basecamp.domain.BasecampBuilder.APPLICANT_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.MEMBER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.NOW;
import static com.pitchmap.basecamp.domain.BasecampBuilder.aBasecamp;
import static com.pitchmap.basecamp.domain.BasecampBuilder.applicationIdOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.error.CommonErrorCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class BasecampApproveTest {

    private static final Instant LATER = NOW.plus(Duration.ofHours(1));
    private static final long SECOND_APPLICANT_ID = 4L;

    @Test
    @DisplayName("[F-13][BC-08] 모집 중 베이스캠프의 대기 신청은 승인할 수 있고, 검사만 하면 그 신청을 돌려주고 아무것도 바꾸지 않는다")
    void checkApprovableReturnsPendingApplicationWithoutChange() {
        // given
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        // when
        BasecampApplication application = basecamp.checkApprovable(applicationIdOf(APPLICANT_ID));

        // then
        assertThat(application.getApplicantId()).isEqualTo(APPLICANT_ID);
        assertThat(application.isPending()).isTrue();
        assertThat(basecamp.headcount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-13][BC-08] 이 베이스캠프의 신청이 아니면 NOT_FOUND이고, 베이스캠프 상태를 보기 전에 가려낸다")
    void unknownApplicationIsNotFoundBeforeStateCheck() {
        // given
        Basecamp recruiting = aBasecamp().inState(BasecampStatus.RECRUITING);
        Basecamp closed = aBasecamp().inState(BasecampStatus.CLOSED);

        // when
        // then
        assertFailsWith(CommonErrorCode.NOT_FOUND, () -> recruiting.checkApprovable(999L));
        assertFailsWith(CommonErrorCode.NOT_FOUND, () -> closed.checkApprovable(999L));
    }

    @ParameterizedTest
    @EnumSource(
            value = BasecampStatus.class,
            names = {"CLOSED", "CONFIRMED", "COMPLETED", "CANCELED"})
    @DisplayName("[F-13][BC-08] 모집 중이 아닌 베이스캠프의 신청은 승인할 수 없다")
    void notRecruitingCannotApprove(BasecampStatus state) {
        // given
        Basecamp basecamp = aBasecamp().inState(state);

        // when
        // then
        assertFailsWithInvalidState(() -> basecamp.checkApprovable(applicationIdOf(APPLICANT_ID)));
        assertFailsWithInvalidState(() -> basecamp.approve(applicationIdOf(APPLICANT_ID), LATER));
    }

    @Test
    @DisplayName("[F-13][BC-08] 이미 승인한 신청이나 거절·취소한 신청은 대기가 아니라서 다시 승인할 수 없다")
    void nonPendingApplicationCannotBeApproved() {
        // given
        Basecamp basecamp = aBasecamp().capacity(6).openOnly();
        BasecampBuilder.join(basecamp, MEMBER_ID);
        BasecampBuilder.apply(basecamp, APPLICANT_ID);
        BasecampBuilder.apply(basecamp, SECOND_APPLICANT_ID);
        basecamp.reject(applicationIdOf(APPLICANT_ID), NOW);
        basecamp.cancelApplication(SECOND_APPLICANT_ID, NOW);

        // when
        // then
        assertFailsWithInvalidState(() -> basecamp.checkApprovable(applicationIdOf(MEMBER_ID)));
        assertFailsWithInvalidState(() -> basecamp.checkApprovable(applicationIdOf(APPLICANT_ID)));
        assertFailsWithInvalidState(() -> basecamp.checkApprovable(applicationIdOf(SECOND_APPLICANT_ID)));
    }

    @Test
    @DisplayName("[F-13][BC-08] 모집 중이어도 정원이 이미 차 있으면 BASECAMP_FULL이다")
    void fullBasecampCannotApprove() {
        // given
        // 정상 흐름에서는 정원이 차면 바로 마감되므로, 마감을 모집 중으로 되돌려 정원 검사만 따로 확인한다.
        Basecamp basecamp = aBasecamp().capacity(3).openOnly();
        BasecampBuilder.join(basecamp, MEMBER_ID);
        BasecampBuilder.apply(basecamp, APPLICANT_ID);
        BasecampBuilder.apply(basecamp, SECOND_APPLICANT_ID);
        basecamp.approve(applicationIdOf(APPLICANT_ID), NOW);
        ReflectionTestUtils.setField(basecamp, "status", BasecampStatus.RECRUITING);

        // when
        // then
        assertFailsWith(
                BasecampErrorCode.BASECAMP_FULL, () -> basecamp.checkApprovable(applicationIdOf(SECOND_APPLICANT_ID)));
        assertFailsWith(
                BasecampErrorCode.BASECAMP_FULL, () -> basecamp.approve(applicationIdOf(SECOND_APPLICANT_ID), LATER));
    }

    @Test
    @DisplayName("[F-13][BC-08] 승인하면 신청이 APPROVED가 되고 멤버가 늘며, 승인한 신청을 돌려준다")
    void approveAddsMemberAndReturnsApplication() {
        // given
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        // when
        BasecampApplication approved = basecamp.approve(applicationIdOf(APPLICANT_ID), LATER);

        // then
        assertThat(approved.getStatus()).isEqualTo(BasecampApplicationStatus.APPROVED);
        assertThat(basecamp.headcount()).isEqualTo(3);
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
    }

    @Test
    @DisplayName("[F-13][BC-08] 마지막 자리를 승인하면 CLOSED와 AUTO_FULL로 자동 마감된다")
    void approveLastSeatClosesAutomatically() {
        // given
        Basecamp basecamp = aBasecamp().capacity(3).openOnly();
        BasecampBuilder.join(basecamp, MEMBER_ID);
        BasecampBuilder.apply(basecamp, APPLICANT_ID);

        // when
        basecamp.approve(applicationIdOf(APPLICANT_ID), LATER);

        // then
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CLOSED);
        assertThat(basecamp.getClosedReason()).isEqualTo(ClosedReason.AUTO_FULL);
    }

    @Test
    @DisplayName("[F-13][BC-07] 거절하면 신청이 REJECTED가 되고 인원은 그대로이며, 거절한 신청을 돌려준다")
    void rejectKeepsHeadcountAndReturnsApplication() {
        // given
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        // when
        BasecampApplication rejected = basecamp.reject(applicationIdOf(APPLICANT_ID), LATER);

        // then
        assertThat(rejected.getStatus()).isEqualTo(BasecampApplicationStatus.REJECTED);
        assertThat(basecamp.headcount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-13][BC-07] 거절할 때도 이 베이스캠프의 신청이 아니면 NOT_FOUND를, 모집 중이 아니면 BASECAMP_INVALID_STATE를 먼저 가린다")
    void rejectChecksApplicationBeforeState() {
        // given
        Basecamp closed = aBasecamp().inState(BasecampStatus.CLOSED);

        // when
        // then
        assertFailsWith(CommonErrorCode.NOT_FOUND, () -> closed.reject(999L, LATER));
        assertFailsWithInvalidState(() -> closed.reject(applicationIdOf(APPLICANT_ID), LATER));
    }
}
