package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampBuilder.APPLICANT_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.DEFAULT_START_DATE;
import static com.pitchmap.basecamp.domain.BasecampBuilder.LEADER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.MEMBER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.OUTSIDER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.aBasecamp;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberRemovalTest {

    private static final Instant LATER = BasecampBuilder.NOW.plus(Duration.ofHours(1));

    @Test
    @DisplayName("[SN-13] 캠프 리더가 제재를 받으면 베이스캠프를 LEADER_SANCTIONED로 취소하고 대기 신청을 만료시킨다")
    void leaderSanctionedCancelsBasecamp() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        MemberRemoval removal = basecamp.removeMember(LEADER_ID, RemovalCause.SANCTION, LATER);

        assertThat(removal).isEqualTo(MemberRemoval.LEADER_CANCELED);
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CANCELED);
        assertThat(basecamp.getCancelReason()).isEqualTo(CancelReason.LEADER_SANCTIONED);
        assertThat(basecamp.getCanceledAt()).isEqualTo(LATER);
        assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.EXPIRED);
        assertThat(applicationOf(basecamp, MEMBER_ID).getStatus()).isEqualTo(BasecampApplicationStatus.APPROVED);
    }

    @Test
    @DisplayName("[SN-13] 확정된 베이스캠프의 캠프 리더가 제재를 받아도 취소한다")
    void leaderOfConfirmedBasecampIsCanceled() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.CONFIRMED);

        MemberRemoval removal = basecamp.removeMember(LEADER_ID, RemovalCause.SANCTION, LATER);

        assertThat(removal).isEqualTo(MemberRemoval.LEADER_CANCELED);
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CANCELED);
    }

    @Test
    @DisplayName("[SN-13] 멤버가 제재를 받으면 LEFT가 되고, 임박 탈퇴 시간대여도 임박 탈퇴로 세지 않는다")
    void sanctionedMemberLeavesWithoutEarlyLeave() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.CONFIRMED);
        Instant withinWindow = DEFAULT_START_DATE.atStartOfDay(Basecamp.KOREA).toInstant();

        MemberRemoval removal = basecamp.removeMember(MEMBER_ID, RemovalCause.SANCTION, withinWindow);

        assertThat(removal).isEqualTo(MemberRemoval.MEMBER_LEFT);
        BasecampMember member = memberOf(basecamp, MEMBER_ID);
        assertThat(member.getStatus()).isEqualTo(BasecampMemberStatus.LEFT);
        assertThat(member.isEarlyLeave()).isFalse();
        assertThat(member.getLeftAt()).isEqualTo(withinWindow);
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CONFIRMED);
    }

    @Test
    @DisplayName("[SN-13] 정원이 차서 자동 마감된 베이스캠프에서 멤버가 빠지면 모집을 다시 연다")
    void autoClosedBasecampReopens() {
        Basecamp basecamp = aBasecamp().autoClosed();
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CLOSED);

        MemberRemoval removal = basecamp.removeMember(MEMBER_ID, RemovalCause.SANCTION, LATER);

        assertThat(removal).isEqualTo(MemberRemoval.MEMBER_LEFT);
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
        assertThat(basecamp.headcount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[SN-13] 대기 중인 신청자가 제재를 받으면 신청이 CANCELED가 되고 멤버는 그대로다")
    void pendingApplicantApplicationIsCanceled() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        MemberRemoval removal = basecamp.removeMember(APPLICANT_ID, RemovalCause.SANCTION, LATER);

        assertThat(removal).isEqualTo(MemberRemoval.APPLICATION_CANCELED);
        assertThat(basecamp.relationOf(APPLICANT_ID)).isEqualTo(BasecampRelation.NONE);
        assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.CANCELED);
        assertThat(basecamp.headcount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[SN-13] 이미 빠진 멤버나 상관없는 회원에게는 아무것도 바꾸지 않고, 두 번째 호출도 변화가 없다")
    void idempotentForAlreadyRemovedOrUnrelated() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
        basecamp.removeMember(MEMBER_ID, RemovalCause.SANCTION, LATER);
        Instant updatedAt = basecamp.getUpdatedAt();

        assertThat(basecamp.removeMember(MEMBER_ID, RemovalCause.SANCTION, LATER.plusSeconds(60)))
                .isEqualTo(MemberRemoval.NOTHING);
        assertThat(basecamp.removeMember(OUTSIDER_ID, RemovalCause.SANCTION, LATER.plusSeconds(60)))
                .isEqualTo(MemberRemoval.NOTHING);

        assertThat(basecamp.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("[SN-13] 취소됐거나 완료된 베이스캠프는 리더든 멤버든 바꾸지 않는다")
    void finishedBasecampIsUntouched() {
        Basecamp canceled = aBasecamp().inState(BasecampStatus.CANCELED);
        Basecamp completed = aBasecamp().inState(BasecampStatus.COMPLETED);

        assertThat(canceled.removeMember(LEADER_ID, RemovalCause.SANCTION, LATER))
                .isEqualTo(MemberRemoval.NOTHING);
        assertThat(canceled.getCancelReason()).isEqualTo(CancelReason.LEADER);
        assertThat(completed.removeMember(MEMBER_ID, RemovalCause.SANCTION, LATER))
                .isEqualTo(MemberRemoval.NOTHING);
        assertThat(memberOf(completed, MEMBER_ID).isActive()).isTrue();
        assertThat(completed.getStatus()).isEqualTo(BasecampStatus.COMPLETED);
    }

    @Test
    @DisplayName("[F-12][PV-02] 캠프 리더가 탈퇴하면 베이스캠프를 LEADER_WITHDRAWN으로 취소하고 대기 신청을 만료시킨다")
    void leaderWithdrawnCancelsBasecamp() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        MemberRemoval removal = basecamp.removeMember(LEADER_ID, RemovalCause.WITHDRAWAL, LATER);

        assertThat(removal).isEqualTo(MemberRemoval.LEADER_CANCELED);
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CANCELED);
        assertThat(basecamp.getCancelReason()).isEqualTo(CancelReason.LEADER_WITHDRAWN);
        assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.EXPIRED);
    }

    @Test
    @DisplayName("[PV-02] 멤버가 탈퇴하면 LEFT가 되고 임박 탈퇴로 세지 않으며, 자동 마감한 베이스캠프는 다시 모집한다")
    void withdrawnMemberLeavesWithoutEarlyLeave() {
        Basecamp confirmed = aBasecamp().inState(BasecampStatus.CONFIRMED);
        Instant withinWindow = DEFAULT_START_DATE.atStartOfDay(Basecamp.KOREA).toInstant();
        Basecamp autoClosed = aBasecamp().autoClosed();

        MemberRemoval removal = confirmed.removeMember(MEMBER_ID, RemovalCause.WITHDRAWAL, withinWindow);
        autoClosed.removeMember(MEMBER_ID, RemovalCause.WITHDRAWAL, LATER);

        assertThat(removal).isEqualTo(MemberRemoval.MEMBER_LEFT);
        assertThat(memberOf(confirmed, MEMBER_ID).getStatus()).isEqualTo(BasecampMemberStatus.LEFT);
        assertThat(memberOf(confirmed, MEMBER_ID).isEarlyLeave()).isFalse();
        assertThat(confirmed.getStatus()).isEqualTo(BasecampStatus.CONFIRMED);
        assertThat(autoClosed.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
    }

    @Test
    @DisplayName("[PV-02] 대기 중인 신청자가 탈퇴하면 신청이 CANCELED가 되고, 끝난 베이스캠프는 바꾸지 않는다")
    void withdrawnApplicantIsCanceledAndFinishedUntouched() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
        Basecamp canceled = aBasecamp().inState(BasecampStatus.CANCELED);

        MemberRemoval removal = basecamp.removeMember(APPLICANT_ID, RemovalCause.WITHDRAWAL, LATER);

        assertThat(removal).isEqualTo(MemberRemoval.APPLICATION_CANCELED);
        assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.CANCELED);
        assertThat(canceled.removeMember(LEADER_ID, RemovalCause.WITHDRAWAL, LATER))
                .isEqualTo(MemberRemoval.NOTHING);
        assertThat(canceled.getCancelReason()).isEqualTo(CancelReason.LEADER);
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
