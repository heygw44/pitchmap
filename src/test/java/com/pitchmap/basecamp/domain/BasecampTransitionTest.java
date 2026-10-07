package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampAssertions.assertFailsWith;
import static com.pitchmap.basecamp.domain.BasecampAssertions.assertFailsWithInvalidState;
import static com.pitchmap.basecamp.domain.BasecampBuilder.APPLICANT_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.COMPLETED_AT;
import static com.pitchmap.basecamp.domain.BasecampBuilder.LEADER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.MEMBER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.NOW;
import static com.pitchmap.basecamp.domain.BasecampBuilder.OUTSIDER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.aBasecamp;
import static com.pitchmap.basecamp.domain.BasecampBuilder.applicationIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.CommonErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BasecampTransitionTest {

    private static final Instant LATER = NOW.plusSeconds(60);

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

    @Nested
    @DisplayName("[02-1 6.2] 상태 전이")
    class StateTransitions {

        @Test
        @DisplayName("모집 중에서 캠프 리더가 마감하면 마감이 되고 마감 이유는 LEADER다")
        void closeByLeader() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            basecamp.close(LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CLOSED);
            assertThat(basecamp.getClosedReason()).isEqualTo(ClosedReason.LEADER);
            assertThat(basecamp.getUpdatedAt()).isEqualTo(LATER);
        }

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"CLOSED", "CONFIRMED", "COMPLETED", "CANCELED"})
        @DisplayName("모집 중이 아니면 마감할 수 없다")
        void closeOnlyWhileRecruiting(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);

            assertFailsWithInvalidState(() -> basecamp.close(LATER));
        }

        @Test
        @DisplayName("마감에서 캠프 리더가 재개하면 모집 중이 되고 마감 이유가 지워진다")
        void reopenByLeader() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.CLOSED);

            basecamp.reopen(LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
            assertThat(basecamp.getClosedReason()).isNull();
        }

        @Test
        @DisplayName("[BC-08] 정원이 가득 찬 마감은 재개할 수 없다")
        void reopenWhileFullFails() {
            Basecamp basecamp = aBasecamp().autoClosed();

            assertFailsWith(BasecampErrorCode.BASECAMP_FULL, () -> basecamp.reopen(LATER));
            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CLOSED);
        }

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"RECRUITING", "CONFIRMED", "COMPLETED", "CANCELED"})
        @DisplayName("마감이 아니면 재개할 수 없다")
        void reopenOnlyWhileClosed(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);

            assertFailsWithInvalidState(() -> basecamp.reopen(LATER));
        }

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"RECRUITING", "CLOSED"})
        @DisplayName("[BC-12] 모집 중과 마감에서 인원이 2명 이상이면 확정한다")
        void confirmWithEnoughMembers(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);

            basecamp.confirm(LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CONFIRMED);
            assertThat(basecamp.getConfirmedAt()).isEqualTo(LATER);
            assertThat(basecamp.getUpdatedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("[BC-12] 인원이 캠프 리더 1명뿐이면 확정할 수 없다")
        void confirmWithLeaderOnlyFails() {
            Basecamp basecamp = aBasecamp().openOnly();

            assertFailsWith(BasecampErrorCode.BASECAMP_NOT_ENOUGH_MEMBERS, () -> basecamp.confirm(LATER));
            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
        }

        @Test
        @DisplayName("[BC-12] 멤버가 탈퇴해 1명이 되면 확정할 수 없다")
        void confirmAfterMemberLeftFails() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
            basecamp.leave(MEMBER_ID, LATER);

            assertFailsWith(BasecampErrorCode.BASECAMP_NOT_ENOUGH_MEMBERS, () -> basecamp.confirm(LATER));
        }

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"CONFIRMED", "COMPLETED", "CANCELED"})
        @DisplayName("모집 중이나 마감이 아니면 확정할 수 없다")
        void confirmOnlyBeforeConfirmed(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);

            assertFailsWithInvalidState(() -> basecamp.confirm(LATER));
        }

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"RECRUITING", "CLOSED", "CONFIRMED"})
        @DisplayName("모집 중, 마감, 확정에서 캠프 리더가 취소하면 취소가 되고 사유와 시각이 남는다")
        void cancelByLeader(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);

            basecamp.cancel(CancelReason.LEADER, LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CANCELED);
            assertThat(basecamp.getCancelReason()).isEqualTo(CancelReason.LEADER);
            assertThat(basecamp.getCanceledAt()).isEqualTo(LATER);
            assertThat(basecamp.getUpdatedAt()).isEqualTo(LATER);
        }

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"COMPLETED", "CANCELED"})
        @DisplayName("완료되거나 이미 취소된 베이스캠프는 취소할 수 없다")
        void cancelFinishedFails(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);

            assertFailsWithInvalidState(() -> basecamp.cancel(CancelReason.LEADER, LATER));
        }

        @Test
        @DisplayName("취소 사유가 null이면 거부한다")
        void cancelWithoutReasonFails() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            assertThatThrownBy(() -> basecamp.cancel(null, LATER)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("확정에서 완료로 바뀌면 완료 시각이 남는다")
        void completeConfirmed() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.CONFIRMED);

            basecamp.complete(COMPLETED_AT);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.COMPLETED);
            assertThat(basecamp.getCompletedAt()).isEqualTo(COMPLETED_AT);
        }

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"RECRUITING", "CLOSED", "COMPLETED", "CANCELED"})
        @DisplayName("확정이 아니면 완료할 수 없다")
        void completeOnlyConfirmed(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);

            assertFailsWithInvalidState(() -> basecamp.complete(COMPLETED_AT));
        }

        @Test
        @DisplayName("[BC-08] 승인해서 정원이 차면 마감이 되고 마감 이유는 AUTO_FULL이다")
        void approveToFullClosesAutomatically() {
            Basecamp basecamp = aBasecamp().capacity(3).openOnly();
            BasecampBuilder.join(basecamp, MEMBER_ID);
            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);

            BasecampBuilder.join(basecamp, APPLICANT_ID);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CLOSED);
            assertThat(basecamp.getClosedReason()).isEqualTo(ClosedReason.AUTO_FULL);
            assertThat(basecamp.headcount()).isEqualTo(3);
            assertThat(basecamp.isFull()).isTrue();
        }
    }

    @Nested
    @DisplayName("대기 신청 정리")
    class PendingApplicationExpiry {

        @Test
        @DisplayName("확정되면 대기 신청이 만료된다")
        void confirmExpiresPending() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            basecamp.confirm(LATER);

            assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.EXPIRED);
        }

        @Test
        @DisplayName("취소되면 대기 신청이 만료된다")
        void cancelExpiresPending() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.CLOSED);

            basecamp.cancel(CancelReason.LEADER, LATER);

            assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.EXPIRED);
        }

        @Test
        @DisplayName("승인된 신청은 확정되어도 승인 상태로 남는다")
        void confirmKeepsApprovedApplications() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            basecamp.confirm(LATER);

            assertThat(applicationOf(basecamp, MEMBER_ID).getStatus()).isEqualTo(BasecampApplicationStatus.APPROVED);
        }

        @Test
        @DisplayName("출발 전날 인원 부족으로 취소돼도 대기 신청이 만료된다")
        void autoCancelExpiresPending() {
            Basecamp basecamp = aBasecamp().openOnly();
            BasecampBuilder.apply(basecamp, APPLICANT_ID);

            basecamp.processDayBeforeDeparture(LATER);

            assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.EXPIRED);
        }
    }

    @Nested
    @DisplayName("[BC-10] 출발 전날 자동 처리")
    class DayBeforeDeparture {

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"RECRUITING", "CLOSED"})
        @DisplayName("인원이 2명이면 자동으로 확정한다")
        void confirmsWithTwoMembers(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);
            assertThat(basecamp.headcount()).isEqualTo(Basecamp.MIN_CONFIRM_HEADCOUNT);

            basecamp.processDayBeforeDeparture(LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CONFIRMED);
            assertThat(basecamp.getConfirmedAt()).isEqualTo(LATER);
        }

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"RECRUITING", "CLOSED"})
        @DisplayName("인원이 1명이면 인원 부족으로 자동 취소한다")
        void cancelsWithOneMember(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().openOnly();
            if (state == BasecampStatus.CLOSED) {
                basecamp.close(NOW);
            }

            basecamp.processDayBeforeDeparture(LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CANCELED);
            assertThat(basecamp.getCancelReason()).isEqualTo(CancelReason.NOT_ENOUGH_MEMBERS);
            assertThat(basecamp.getCanceledAt()).isEqualTo(LATER);
        }

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"CONFIRMED", "COMPLETED", "CANCELED"})
        @DisplayName("모집 중이나 마감이 아니면 자동 처리 대상이 아니다")
        void ignoresOtherStates(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);

            assertFailsWithInvalidState(() -> basecamp.processDayBeforeDeparture(LATER));
        }

        @Test
        @DisplayName("확정된 뒤 인원이 1명이 되어도 확정은 유지된다")
        void confirmedStaysConfirmedAfterMemberLeaves() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.CONFIRMED);

            basecamp.leave(MEMBER_ID, LATER);

            assertThat(basecamp.headcount()).isEqualTo(1);
            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CONFIRMED);
        }
    }

    @Nested
    @DisplayName("[BC-11] 자동 마감 뒤 빈자리가 생기면 모집 재개")
    class AutoReopen {

        @Test
        @DisplayName("자동 마감된 베이스캠프는 멤버가 탈퇴하면 모집을 다시 연다")
        void reopensWhenMemberLeaves() {
            Basecamp basecamp = aBasecamp().autoClosed();

            basecamp.leave(MEMBER_ID, LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
            assertThat(basecamp.getClosedReason()).isNull();
            assertThat(basecamp.getUpdatedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("자동 마감된 베이스캠프는 멤버가 강퇴되면 모집을 다시 연다")
        void reopensWhenMemberKicked() {
            Basecamp basecamp = aBasecamp().autoClosed();

            basecamp.kick(MEMBER_ID, KickReason.OTHER, LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
            assertThat(basecamp.getClosedReason()).isNull();
        }

        @Test
        @DisplayName("자동 마감된 베이스캠프는 정원을 늘려 빈자리가 생기면 모집을 다시 연다")
        void reopensWhenCapacityIncreases() {
            Basecamp basecamp = aBasecamp().autoClosed();
            BasecampRevision revision = new BasecampRevision("제목", "설명", Capacity.of(4), JoinCondition.none());

            basecamp.revise(revision, LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
            assertThat(basecamp.getClosedReason()).isNull();
        }

        @Test
        @DisplayName("자동 마감된 베이스캠프는 정원을 그대로 두면 계속 마감이다")
        void staysClosedWhenCapacityUnchanged() {
            Basecamp basecamp = aBasecamp().autoClosed();
            BasecampRevision revision = new BasecampRevision("제목", "설명", Capacity.of(3), JoinCondition.none());

            basecamp.revise(revision, LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CLOSED);
            assertThat(basecamp.getClosedReason()).isEqualTo(ClosedReason.AUTO_FULL);
        }

        @Test
        @DisplayName("캠프 리더가 직접 마감한 베이스캠프는 멤버가 탈퇴해도 마감으로 둔다")
        void leaderClosedStaysClosedWhenMemberLeaves() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.CLOSED);

            basecamp.leave(MEMBER_ID, LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CLOSED);
            assertThat(basecamp.getClosedReason()).isEqualTo(ClosedReason.LEADER);
        }

        @Test
        @DisplayName("캠프 리더가 직접 마감한 베이스캠프는 멤버가 강퇴돼도 마감으로 둔다")
        void leaderClosedStaysClosedWhenMemberKicked() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.CLOSED);

            basecamp.kick(MEMBER_ID, KickReason.OTHER, LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CLOSED);
        }

        @Test
        @DisplayName("캠프 리더가 직접 마감한 베이스캠프는 정원을 늘려도 마감으로 둔다")
        void leaderClosedStaysClosedWhenCapacityIncreases() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.CLOSED);
            BasecampRevision revision = new BasecampRevision("제목", "설명", Capacity.of(6), JoinCondition.none());

            basecamp.revise(revision, LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CLOSED);
            assertThat(basecamp.getClosedReason()).isEqualTo(ClosedReason.LEADER);
        }

        @Test
        @DisplayName("모집 중인 베이스캠프는 멤버가 탈퇴해도 상태가 그대로다")
        void recruitingStaysRecruiting() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            basecamp.leave(MEMBER_ID, LATER);

            assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
        }
    }

    @Nested
    @DisplayName("[BC-20][BC-22] 탈퇴와 강퇴")
    class LeaveAndKick {

        @ParameterizedTest
        @EnumSource(
                value = BasecampStatus.class,
                names = {"RECRUITING", "CLOSED", "CONFIRMED"})
        @DisplayName("[BC-20] 캠프 리더는 탈퇴할 수 없다")
        void leaderCannotLeave(BasecampStatus state) {
            Basecamp basecamp = aBasecamp().inState(state);

            assertFailsWith(BasecampErrorCode.BASECAMP_LEADER_CANNOT_LEAVE, () -> basecamp.leave(LEADER_ID, LATER));
            assertThat(memberOf(basecamp, LEADER_ID).isActive()).isTrue();
        }

        @Test
        @DisplayName("[BC-20] 캠프 리더는 강퇴할 수 없다")
        void leaderCannotBeKicked() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            assertFailsWith(
                    BasecampErrorCode.BASECAMP_LEADER_CANNOT_LEAVE,
                    () -> basecamp.kick(LEADER_ID, KickReason.OTHER, LATER));
        }

        @Test
        @DisplayName("탈퇴하면 멤버가 LEFT가 되고 탈퇴 시각이 남아 인원이 줄어든다")
        void leaveMarksMember() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            basecamp.leave(MEMBER_ID, LATER);

            BasecampMember member = memberOf(basecamp, MEMBER_ID);
            assertThat(member.getStatus()).isEqualTo(BasecampMemberStatus.LEFT);
            assertThat(member.getLeftAt()).isEqualTo(LATER);
            assertThat(basecamp.headcount()).isEqualTo(1);
        }

        @Test
        @DisplayName("멤버가 아니거나 이미 탈퇴한 회원의 탈퇴·강퇴는 NOT_FOUND다")
        void unknownOrInactiveMemberIsNotFound() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
            basecamp.leave(MEMBER_ID, LATER);

            assertFailsWith(CommonErrorCode.NOT_FOUND, () -> basecamp.leave(OUTSIDER_ID, LATER));
            assertFailsWith(CommonErrorCode.NOT_FOUND, () -> basecamp.leave(MEMBER_ID, LATER));
            assertFailsWith(CommonErrorCode.NOT_FOUND, () -> basecamp.kick(OUTSIDER_ID, KickReason.OTHER, LATER));
        }

        @ParameterizedTest
        @EnumSource(KickReason.class)
        @DisplayName("[BC-22] 강퇴하면 멤버가 KICKED가 되고 사유와 시각이 남는다")
        void kickRecordsReason(KickReason reason) {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            basecamp.kick(MEMBER_ID, reason, LATER);

            BasecampMember member = memberOf(basecamp, MEMBER_ID);
            assertThat(member.getStatus()).isEqualTo(BasecampMemberStatus.KICKED);
            assertThat(member.getKickReason()).isEqualTo(reason);
            assertThat(member.getLeftAt()).isEqualTo(LATER);
            assertThat(basecamp.headcount()).isEqualTo(1);
        }

        @Test
        @DisplayName("[BC-22] 강퇴 사유가 null이면 거부하고 멤버는 그대로다")
        void kickRequiresReason() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            assertThatThrownBy(() -> basecamp.kick(MEMBER_ID, null, LATER))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(memberOf(basecamp, MEMBER_ID).isActive()).isTrue();
        }

        @Test
        @DisplayName("[BC-22] 확정된 뒤에는 강퇴할 수 없다")
        void kickAfterConfirmFails() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.CONFIRMED);

            assertFailsWithInvalidState(() -> basecamp.kick(MEMBER_ID, KickReason.OTHER, LATER));
        }

        @Test
        @DisplayName("[BC-07][BC-22] 강퇴된 회원은 다시 신청할 수 없다")
        void kickedCannotReapply() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
            basecamp.kick(MEMBER_ID, KickReason.NO_CONTACT, LATER);

            assertFailsWith(
                    BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED, () -> basecamp.apply(MEMBER_ID, "다시", LATER));
        }

        @Test
        @DisplayName("[BC-07] 탈퇴한 회원은 다시 신청할 수 없다")
        void leftCannotReapply() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
            basecamp.leave(MEMBER_ID, LATER);

            assertFailsWith(
                    BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED, () -> basecamp.apply(MEMBER_ID, "다시", LATER));
        }
    }

    @Nested
    @DisplayName("[BC-06][BC-07] 합류 신청과 결정")
    class Applications {

        @Test
        @DisplayName("새 회원이 신청하면 대기 상태 신청이 목록에 들어간다")
        void applyCreatesPendingApplication() {
            Basecamp basecamp = aBasecamp().openOnly();

            BasecampApplication application = basecamp.apply(APPLICANT_ID, "같이 가요", NOW);

            assertThat(application.getStatus()).isEqualTo(BasecampApplicationStatus.PENDING);
            assertThat(application.getMessage()).isEqualTo("같이 가요");
            assertThat(application.getCreatedAt()).isEqualTo(NOW);
            assertThat(basecamp.getApplications()).containsExactly(application);
        }

        @Test
        @DisplayName("이미 대기 중인 회원이 다시 신청하면 BASECAMP_ALREADY_APPLIED다")
        void pendingCannotApplyAgain() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            assertFailsWith(BasecampErrorCode.BASECAMP_ALREADY_APPLIED, () -> basecamp.apply(APPLICANT_ID, "또", LATER));
        }

        @Test
        @DisplayName("이미 멤버인 회원과 캠프 리더가 신청하면 BASECAMP_ALREADY_APPLIED다")
        void membersCannotApply() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            assertFailsWith(BasecampErrorCode.BASECAMP_ALREADY_APPLIED, () -> basecamp.apply(MEMBER_ID, "또", LATER));
            assertFailsWith(BasecampErrorCode.BASECAMP_ALREADY_APPLIED, () -> basecamp.apply(LEADER_ID, "또", LATER));
        }

        @Test
        @DisplayName("[BC-07] 거절된 회원은 다시 신청할 수 없다")
        void rejectedCannotReapply() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
            basecamp.reject(applicationIdOf(APPLICANT_ID), LATER);

            assertFailsWith(
                    BasecampErrorCode.BASECAMP_REAPPLY_NOT_ALLOWED, () -> basecamp.apply(APPLICANT_ID, "다시", LATER));
        }

        @Test
        @DisplayName("[BC-07] 스스로 취소한 회원은 같은 신청 행이 대기로 되돌아가며 다시 신청한다")
        void canceledReusesSameRow() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
            BasecampApplication original = applicationOf(basecamp, APPLICANT_ID);
            basecamp.cancelApplication(APPLICANT_ID, LATER);
            Instant resubmittedAt = LATER.plusSeconds(60);

            BasecampApplication again = basecamp.apply(APPLICANT_ID, "마음이 바뀌었어요", resubmittedAt);

            assertThat(again).isSameAs(original);
            assertThat(again.getStatus()).isEqualTo(BasecampApplicationStatus.PENDING);
            assertThat(again.getMessage()).isEqualTo("마음이 바뀌었어요");
            assertThat(again.getUpdatedAt()).isEqualTo(resubmittedAt);
            assertThat(basecamp.getApplications()).hasSize(2);
        }

        @Test
        @DisplayName("신청 메시지가 500자를 넘으면 거부한다")
        void rejectsLongMessage() {
            Basecamp basecamp = aBasecamp().openOnly();

            assertThat(basecamp.apply(APPLICANT_ID, "가".repeat(500), NOW)).isNotNull();
            assertThatThrownBy(() -> basecamp.apply(OUTSIDER_ID, "가".repeat(501), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("승인하면 신청이 APPROVED가 되고 결정 시각이 남으며 멤버가 늘어난다")
        void approveAddsMember() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            basecamp.approve(applicationIdOf(APPLICANT_ID), LATER);

            BasecampApplication application = applicationOf(basecamp, APPLICANT_ID);
            assertThat(application.getStatus()).isEqualTo(BasecampApplicationStatus.APPROVED);
            assertThat(application.getDecidedAt()).isEqualTo(LATER);
            BasecampMember member = memberOf(basecamp, APPLICANT_ID);
            assertThat(member.isActive()).isTrue();
            assertThat(member.isLeader()).isFalse();
            assertThat(member.getJoinedAt()).isEqualTo(LATER);
            assertThat(basecamp.headcount()).isEqualTo(3);
        }

        @Test
        @DisplayName("거절하면 신청이 REJECTED가 되고 결정 시각이 남으며 인원은 그대로다")
        void rejectKeepsHeadcount() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            basecamp.reject(applicationIdOf(APPLICANT_ID), LATER);

            BasecampApplication application = applicationOf(basecamp, APPLICANT_ID);
            assertThat(application.getStatus()).isEqualTo(BasecampApplicationStatus.REJECTED);
            assertThat(application.getDecidedAt()).isEqualTo(LATER);
            assertThat(basecamp.headcount()).isEqualTo(2);
        }

        @Test
        @DisplayName("이미 결정된 신청을 다시 승인하거나 거절하면 BASECAMP_INVALID_STATE다")
        void decidedApplicationCannotBeDecidedAgain() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
            long memberApplicationId = applicationIdOf(MEMBER_ID);

            assertFailsWithInvalidState(() -> basecamp.approve(memberApplicationId, LATER));
            assertFailsWithInvalidState(() -> basecamp.reject(memberApplicationId, LATER));
            assertThat(basecamp.headcount()).isEqualTo(2);
        }

        @Test
        @DisplayName("없는 신청을 승인·거절하거나 신청하지 않은 회원이 취소하면 NOT_FOUND다")
        void unknownApplicationIsNotFound() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            assertFailsWith(CommonErrorCode.NOT_FOUND, () -> basecamp.approve(999L, LATER));
            assertFailsWith(CommonErrorCode.NOT_FOUND, () -> basecamp.reject(999L, LATER));
            assertFailsWith(CommonErrorCode.NOT_FOUND, () -> basecamp.cancelApplication(OUTSIDER_ID, LATER));
        }

        @Test
        @DisplayName("이미 승인된 신청은 신청자가 취소할 수 없다")
        void approvedApplicationCannotBeCanceled() {
            Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

            assertFailsWithInvalidState(() -> basecamp.cancelApplication(MEMBER_ID, LATER));
        }
    }
}
