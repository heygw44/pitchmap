package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampAssertions.assertFailsWithInvalidState;
import static com.pitchmap.basecamp.domain.BasecampBuilder.APPLICANT_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.MEMBER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.NOW;
import static com.pitchmap.basecamp.domain.BasecampBuilder.OUTSIDER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.aBasecamp;
import static com.pitchmap.basecamp.domain.BasecampBuilder.applicationIdOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** 상태별로 할 수 있는 일 표의 칸을 하나도 빠짐없이 확인한다. 완료와 취소는 서로 다른 상태라서 따로 센다. */
class BasecampStateActionTest {

    private static final Instant LATER = NOW.plusSeconds(60);
    private static final long NEW_APPLICANT_ID = 4L;

    private static Stream<Arguments> cases(BasecampStatus... allowedStates) {
        Set<BasecampStatus> allowed = Set.of(allowedStates);
        return Stream.of(BasecampStatus.values()).map(state -> Arguments.of(state, allowed.contains(state)));
    }

    static Stream<Arguments> applyCases() {
        return cases(BasecampStatus.RECRUITING);
    }

    static Stream<Arguments> approveRejectCases() {
        return cases(BasecampStatus.RECRUITING);
    }

    static Stream<Arguments> cancelApplicationCases() {
        return cases(BasecampStatus.RECRUITING, BasecampStatus.CLOSED);
    }

    static Stream<Arguments> leaveCases() {
        return cases(BasecampStatus.RECRUITING, BasecampStatus.CLOSED, BasecampStatus.CONFIRMED);
    }

    static Stream<Arguments> kickCases() {
        return cases(BasecampStatus.RECRUITING, BasecampStatus.CLOSED);
    }

    static Stream<Arguments> reviseCases() {
        return cases(BasecampStatus.RECRUITING, BasecampStatus.CLOSED);
    }

    private static Basecamp basecampIn(BasecampStatus state) {
        return aBasecamp().inState(state);
    }

    private static BasecampRevision revision(int capacity) {
        return new BasecampRevision("바뀐 제목", "바뀐 설명", Capacity.of(capacity), JoinCondition.none());
    }

    @ParameterizedTest(name = "{0} 상태에서 허용={1}")
    @MethodSource("applyCases")
    @DisplayName("[02-1 6.3] 합류 신청은 모집 중에만 할 수 있다")
    void apply(BasecampStatus state, boolean allowed) {
        Basecamp basecamp = basecampIn(state);

        if (allowed) {
            BasecampApplication application = basecamp.apply(NEW_APPLICANT_ID, "같이 가요", NOW);
            assertThat(application.getStatus()).isEqualTo(BasecampApplicationStatus.PENDING);
            return;
        }
        assertFailsWithInvalidState(() -> basecamp.apply(NEW_APPLICANT_ID, "같이 가요", NOW));
    }

    @ParameterizedTest(name = "{0} 상태에서 허용={1}")
    @MethodSource("approveRejectCases")
    @DisplayName("[02-1 6.3] 신청 승인은 모집 중에만 할 수 있다")
    void approve(BasecampStatus state, boolean allowed) {
        Basecamp basecamp = basecampIn(state);

        if (allowed) {
            basecamp.approve(applicationIdOf(APPLICANT_ID), LATER);
            assertThat(basecamp.headcount()).isEqualTo(3);
            return;
        }
        assertFailsWithInvalidState(() -> basecamp.approve(applicationIdOf(APPLICANT_ID), LATER));
    }

    @ParameterizedTest(name = "{0} 상태에서 허용={1}")
    @MethodSource("approveRejectCases")
    @DisplayName("[02-1 6.3] 신청 거절은 모집 중에만 할 수 있다")
    void reject(BasecampStatus state, boolean allowed) {
        Basecamp basecamp = basecampIn(state);

        if (allowed) {
            basecamp.reject(applicationIdOf(APPLICANT_ID), LATER);
            assertThat(basecamp.headcount()).isEqualTo(2);
            return;
        }
        assertFailsWithInvalidState(() -> basecamp.reject(applicationIdOf(APPLICANT_ID), LATER));
    }

    @ParameterizedTest(name = "{0} 상태에서 허용={1}")
    @MethodSource("cancelApplicationCases")
    @DisplayName("[02-1 6.3] 신청 취소는 모집 중과 마감에서만 할 수 있다")
    void cancelApplication(BasecampStatus state, boolean allowed) {
        Basecamp basecamp = basecampIn(state);

        if (allowed) {
            basecamp.cancelApplication(APPLICANT_ID, LATER);
            assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.CANCELED);
            return;
        }
        assertFailsWithInvalidState(() -> basecamp.cancelApplication(APPLICANT_ID, LATER));
    }

    @Test
    @DisplayName("[02-1 6.3] 확정되면 대기 신청이 만료되어 신청 취소 칸은 해당 없음이고, 취소를 시도하면 거부한다")
    void cancelApplicationAfterConfirmIsNotApplicable() {
        Basecamp basecamp = basecampIn(BasecampStatus.RECRUITING);
        basecamp.confirm(NOW);

        assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.EXPIRED);
        assertFailsWithInvalidState(() -> basecamp.cancelApplication(APPLICANT_ID, LATER));
    }

    @ParameterizedTest(name = "{0} 상태에서 허용={1}")
    @MethodSource("leaveCases")
    @DisplayName("[02-1 6.3] 탈퇴는 모집 중, 마감, 확정에서 할 수 있다")
    void leave(BasecampStatus state, boolean allowed) {
        Basecamp basecamp = basecampIn(state);

        if (allowed) {
            basecamp.leave(MEMBER_ID, LATER);
            assertThat(memberOf(basecamp, MEMBER_ID).getStatus()).isEqualTo(BasecampMemberStatus.LEFT);
            return;
        }
        assertFailsWithInvalidState(() -> basecamp.leave(MEMBER_ID, LATER));
    }

    @ParameterizedTest(name = "{0} 상태에서 허용={1}")
    @MethodSource("kickCases")
    @DisplayName("[02-1 6.3] 강퇴는 모집 중과 마감에서만 할 수 있다")
    void kick(BasecampStatus state, boolean allowed) {
        Basecamp basecamp = basecampIn(state);

        if (allowed) {
            basecamp.kick(MEMBER_ID, KickReason.NO_CONTACT, LATER);
            assertThat(memberOf(basecamp, MEMBER_ID).getStatus()).isEqualTo(BasecampMemberStatus.KICKED);
            return;
        }
        assertFailsWithInvalidState(() -> basecamp.kick(MEMBER_ID, KickReason.NO_CONTACT, LATER));
    }

    @ParameterizedTest(name = "{0} 상태에서 허용={1}")
    @MethodSource("reviseCases")
    @DisplayName("[02-1 6.3] 정보 수정은 모집 중과 마감에서만 할 수 있다")
    void revise(BasecampStatus state, boolean allowed) {
        Basecamp basecamp = basecampIn(state);

        if (allowed) {
            basecamp.revise(revision(5), LATER);
            assertThat(basecamp.getTitle()).isEqualTo("바뀐 제목");
            assertThat(basecamp.getCapacity()).isEqualTo(Capacity.of(5));
            return;
        }
        assertFailsWithInvalidState(() -> basecamp.revise(revision(5), LATER));
    }

    @ParameterizedTest(name = "{0} 상태에서 정원 축소는 거부")
    @EnumSource(
            value = BasecampStatus.class,
            names = {"RECRUITING", "CLOSED"})
    @DisplayName("[02-1 6.3] 정보 수정으로 정원을 줄일 수 없다")
    void reviseRejectsCapacityReduction(BasecampStatus state) {
        Basecamp basecamp = basecampIn(state);

        BasecampAssertions.assertFailsWith(
                BasecampErrorCode.BASECAMP_CAPACITY_INVALID, () -> basecamp.revise(revision(3), LATER));
        assertThat(basecamp.getCapacity()).isEqualTo(Capacity.of(4));
    }

    @Test
    @DisplayName("[02-1 6.3] 정보 수정은 출발일, 종료일, 장소를 바꾸지 않는다")
    void reviseKeepsDatesAndSpot() {
        Basecamp basecamp = basecampIn(BasecampStatus.RECRUITING);

        basecamp.revise(revision(4), LATER);

        assertThat(basecamp.getStartDate()).isEqualTo(BasecampBuilder.DEFAULT_START_DATE);
        assertThat(basecamp.getEndDate()).isEqualTo(BasecampBuilder.DEFAULT_START_DATE.plusDays(2));
        assertThat(basecamp.getSpotId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("[02-1 6.3] 연락 수단은 모집 중에는 멤버에게도 보이지 않는다")
    void contactHiddenWhileRecruiting() {
        Basecamp basecamp = basecampIn(BasecampStatus.RECRUITING);

        assertThat(basecamp.canViewContact(MEMBER_ID, NOW)).isFalse();
        assertThat(basecamp.canViewContact(BasecampBuilder.LEADER_ID, NOW)).isFalse();
    }

    @Test
    @DisplayName("[02-1 6.3] 연락 수단은 마감 상태에서도 멤버에게 보이지 않는다")
    void contactHiddenWhileClosed() {
        Basecamp basecamp = basecampIn(BasecampStatus.CLOSED);

        assertThat(basecamp.canViewContact(MEMBER_ID, NOW)).isFalse();
        assertThat(basecamp.canViewContact(BasecampBuilder.LEADER_ID, NOW)).isFalse();
    }

    @Test
    @DisplayName("[02-1 6.3] 연락 수단은 확정되면 멤버와 캠프 리더에게만 보인다")
    void contactVisibleToConfirmedMembersOnly() {
        Basecamp basecamp = basecampIn(BasecampStatus.CONFIRMED);

        assertThat(basecamp.canViewContact(MEMBER_ID, NOW)).isTrue();
        assertThat(basecamp.canViewContact(BasecampBuilder.LEADER_ID, NOW)).isTrue();
        assertThat(basecamp.canViewContact(APPLICANT_ID, NOW)).isFalse();
        assertThat(basecamp.canViewContact(OUTSIDER_ID, NOW)).isFalse();
    }

    @Test
    @DisplayName("[02-1 6.3] 연락 수단은 완료되면 7일까지 멤버에게만 보인다")
    void contactVisibleToMembersAfterCompletion() {
        Basecamp basecamp = basecampIn(BasecampStatus.COMPLETED);

        assertThat(basecamp.canViewContact(MEMBER_ID, BasecampBuilder.COMPLETED_AT))
                .isTrue();
        assertThat(basecamp.canViewContact(OUTSIDER_ID, BasecampBuilder.COMPLETED_AT))
                .isFalse();
    }

    @Test
    @DisplayName("[02-1 6.3] 연락 수단은 취소되면 아무에게도 보이지 않는다")
    void contactHiddenAfterCancel() {
        Basecamp basecamp = basecampIn(BasecampStatus.CANCELED);

        assertThat(basecamp.canViewContact(MEMBER_ID, NOW)).isFalse();
        assertThat(basecamp.canViewContact(BasecampBuilder.LEADER_ID, NOW)).isFalse();
    }

    @Test
    @DisplayName("[02-1 6.3] 확정 뒤 탈퇴한 멤버에게는 연락 수단이 보이지 않는다")
    void contactHiddenToLeftMember() {
        Basecamp basecamp = basecampIn(BasecampStatus.CONFIRMED);
        basecamp.leave(MEMBER_ID, LATER);

        assertThat(basecamp.canViewContact(MEMBER_ID, LATER)).isFalse();
    }

    private static BasecampApplication applicationOf(Basecamp basecamp, long applicantId) {
        return basecamp.getApplications().stream()
                .filter(application -> application.isFrom(applicantId))
                .findFirst()
                .orElseThrow();
    }

    private static BasecampMember memberOf(Basecamp basecamp, long memberId) {
        return basecamp.getMembers().stream()
                .filter(member -> member.getMemberId() == memberId)
                .findFirst()
                .orElseThrow();
    }
}
