package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampAssertions.assertFailsWith;
import static com.pitchmap.basecamp.domain.BasecampAssertions.assertFailsWithInvalidState;
import static com.pitchmap.basecamp.domain.BasecampBuilder.APPLICANT_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.LEADER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.MEMBER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.NOW;
import static com.pitchmap.basecamp.domain.BasecampBuilder.OUTSIDER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.aBasecamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BasecampApplyTest {

    private static final long FIRST_EXTRA_APPLICANT_ID = 100L;

    // 정원 6명인 모집 중 베이스캠프에 서로 다른 회원 count명이 대기 신청을 낸 상태를 만든다.
    private static Basecamp basecampWithPending(int count) {
        Basecamp basecamp = aBasecamp().capacity(6).openOnly();
        for (int i = 0; i < count; i++) {
            BasecampBuilder.apply(basecamp, FIRST_EXTRA_APPLICANT_ID + i);
        }
        return basecamp;
    }

    @Test
    @DisplayName("[F-13][BC-07] 대기 신청이 19건이면 20번째 신청을 받는다")
    void acceptsTwentiethPendingApplication() {
        // given
        Basecamp basecamp = basecampWithPending(Basecamp.MAX_PENDING_APPLICATIONS - 1);

        // when
        BasecampApplication application = basecamp.apply(OUTSIDER_ID, "같이 가요", NOW);

        // then
        assertThat(application.isPending()).isTrue();
        assertThat(basecamp.getApplications())
                .filteredOn(BasecampApplication::isPending)
                .hasSize(20);
    }

    @Test
    @DisplayName("[F-13][BC-07] 대기 신청이 20건이면 새 신청은 BASECAMP_PENDING_LIMIT으로 거부하고 신청 행을 늘리지 않는다")
    void rejectsApplicationOverPendingLimit() {
        // given
        Basecamp basecamp = basecampWithPending(Basecamp.MAX_PENDING_APPLICATIONS);

        // when
        // then
        assertFailsWith(BasecampErrorCode.BASECAMP_PENDING_LIMIT, () -> basecamp.apply(OUTSIDER_ID, "같이 가요", NOW));
        assertThat(basecamp.getApplications()).hasSize(20);
    }

    @Test
    @DisplayName("[F-13][BC-07] 대기 신청이 20건일 때 스스로 취소했던 회원이 다시 신청해도 BASECAMP_PENDING_LIMIT이다")
    void rejectsResubmitOverPendingLimit() {
        // given
        Basecamp basecamp = basecampWithPending(Basecamp.MAX_PENDING_APPLICATIONS - 1);
        basecamp.apply(OUTSIDER_ID, "같이 가요", NOW);
        basecamp.cancelApplication(OUTSIDER_ID, NOW);
        BasecampBuilder.apply(basecamp, FIRST_EXTRA_APPLICANT_ID + 50);

        // when
        // then
        assertFailsWith(BasecampErrorCode.BASECAMP_PENDING_LIMIT, () -> basecamp.apply(OUTSIDER_ID, "다시요", NOW));
    }

    @Test
    @DisplayName("[F-13][BC-07] 이미 신청한 회원은 대기 신청이 20건이어도 BASECAMP_PENDING_LIMIT이 아니라 BASECAMP_ALREADY_APPLIED다")
    void alreadyAppliedTakesPrecedenceOverPendingLimit() {
        // given
        Basecamp basecamp = basecampWithPending(Basecamp.MAX_PENDING_APPLICATIONS);

        // when
        // then
        assertFailsWith(
                BasecampErrorCode.BASECAMP_ALREADY_APPLIED, () -> basecamp.apply(FIRST_EXTRA_APPLICANT_ID, "또요", NOW));
    }

    @ParameterizedTest
    @EnumSource(BasecampStatus.class)
    @DisplayName("[F-13][BC-06] checkApplicable은 모집 중이면 통과하고, 그 밖의 상태에서는 BASECAMP_INVALID_STATE다")
    void checkApplicableByStatus(BasecampStatus state) {
        // given
        Basecamp basecamp = aBasecamp().inState(state);

        // when
        // then
        if (state == BasecampStatus.RECRUITING) {
            assertThatCode(() -> basecamp.checkApplicable(OUTSIDER_ID)).doesNotThrowAnyException();
            return;
        }
        assertFailsWithInvalidState(() -> basecamp.checkApplicable(OUTSIDER_ID));
    }

    @Test
    @DisplayName("[F-13][BC-07] checkApplicable은 대기 중인 신청자, 멤버, 캠프 리더를 BASECAMP_ALREADY_APPLIED로 거부한다")
    void checkApplicableRejectsPendingApplicantMemberAndLeader() {
        // given
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        // when
        // then
        assertFailsWith(BasecampErrorCode.BASECAMP_ALREADY_APPLIED, () -> basecamp.checkApplicable(APPLICANT_ID));
        assertFailsWith(BasecampErrorCode.BASECAMP_ALREADY_APPLIED, () -> basecamp.checkApplicable(MEMBER_ID));
        assertFailsWith(BasecampErrorCode.BASECAMP_ALREADY_APPLIED, () -> basecamp.checkApplicable(LEADER_ID));
    }

    @Test
    @DisplayName("[F-13][BC-07] checkApplicable은 거절된 신청자와 탈퇴한 멤버를 BASECAMP_REAPPLY_NOT_ALLOWED로 거부한다")
    void checkApplicableRejectsRejectedAndLeftMembers() {
        // given
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
        basecamp.reject(BasecampBuilder.applicationIdOf(APPLICANT_ID), NOW);
        basecamp.leave(MEMBER_ID, NOW);

        // when
        // then
        assertFailsWith(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED, () -> basecamp.checkApplicable(APPLICANT_ID));
        assertFailsWith(BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED, () -> basecamp.checkApplicable(MEMBER_ID));
    }

    @Test
    @DisplayName("[F-13][BC-07] checkApplicable은 스스로 취소한 신청자를 통과시키고 상태를 바꾸지 않는다")
    void checkApplicablePassesCanceledApplicantWithoutChangingState() {
        // given
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
        basecamp.cancelApplication(APPLICANT_ID, NOW);

        // when
        basecamp.checkApplicable(APPLICANT_ID);

        // then
        assertThat(basecamp.relationOf(APPLICANT_ID)).isEqualTo(BasecampRelation.NONE);
    }
}
