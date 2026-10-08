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

class SanctionRemovalTest {

    private static final Instant LATER = BasecampBuilder.NOW.plus(Duration.ofHours(1));

    @Test
    @DisplayName("[SN-13] 캠프 리더가 제재를 받으면 베이스캠프를 LEADER_SANCTIONED로 취소하고 대기 신청을 만료시킨다")
    void leaderSanctionedCancelsBasecamp() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        SanctionRemoval removal = basecamp.removeSanctionedMember(LEADER_ID, LATER);

        assertThat(removal).isEqualTo(SanctionRemoval.LEADER_CANCELED);
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

        SanctionRemoval removal = basecamp.removeSanctionedMember(LEADER_ID, LATER);

        assertThat(removal).isEqualTo(SanctionRemoval.LEADER_CANCELED);
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.CANCELED);
    }

    @Test
    @DisplayName("[SN-13] 멤버가 제재를 받으면 LEFT가 되고, 임박 탈퇴 시간대여도 임박 탈퇴로 세지 않는다")
    void sanctionedMemberLeavesWithoutEarlyLeave() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.CONFIRMED);
        Instant withinWindow = DEFAULT_START_DATE.atStartOfDay(Basecamp.KOREA).toInstant();

        SanctionRemoval removal = basecamp.removeSanctionedMember(MEMBER_ID, withinWindow);

        assertThat(removal).isEqualTo(SanctionRemoval.MEMBER_LEFT);
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

        SanctionRemoval removal = basecamp.removeSanctionedMember(MEMBER_ID, LATER);

        assertThat(removal).isEqualTo(SanctionRemoval.MEMBER_LEFT);
        assertThat(basecamp.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
        assertThat(basecamp.headcount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[SN-13] 대기 중인 신청자가 제재를 받으면 신청이 CANCELED가 되고 멤버는 그대로다")
    void pendingApplicantApplicationIsCanceled() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        SanctionRemoval removal = basecamp.removeSanctionedMember(APPLICANT_ID, LATER);

        assertThat(removal).isEqualTo(SanctionRemoval.APPLICATION_CANCELED);
        assertThat(basecamp.relationOf(APPLICANT_ID)).isEqualTo(BasecampRelation.NONE);
        assertThat(applicationOf(basecamp, APPLICANT_ID).getStatus()).isEqualTo(BasecampApplicationStatus.CANCELED);
        assertThat(basecamp.headcount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[SN-13] 이미 빠진 멤버나 상관없는 회원에게는 아무것도 바꾸지 않고, 두 번째 호출도 변화가 없다")
    void idempotentForAlreadyRemovedOrUnrelated() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
        basecamp.removeSanctionedMember(MEMBER_ID, LATER);
        Instant updatedAt = basecamp.getUpdatedAt();

        assertThat(basecamp.removeSanctionedMember(MEMBER_ID, LATER.plusSeconds(60)))
                .isEqualTo(SanctionRemoval.NOTHING);
        assertThat(basecamp.removeSanctionedMember(OUTSIDER_ID, LATER.plusSeconds(60)))
                .isEqualTo(SanctionRemoval.NOTHING);

        assertThat(basecamp.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("[SN-13] 취소됐거나 완료된 베이스캠프는 리더든 멤버든 바꾸지 않는다")
    void finishedBasecampIsUntouched() {
        Basecamp canceled = aBasecamp().inState(BasecampStatus.CANCELED);
        Basecamp completed = aBasecamp().inState(BasecampStatus.COMPLETED);

        assertThat(canceled.removeSanctionedMember(LEADER_ID, LATER)).isEqualTo(SanctionRemoval.NOTHING);
        assertThat(canceled.getCancelReason()).isEqualTo(CancelReason.LEADER);
        assertThat(completed.removeSanctionedMember(MEMBER_ID, LATER)).isEqualTo(SanctionRemoval.NOTHING);
        assertThat(memberOf(completed, MEMBER_ID).isActive()).isTrue();
        assertThat(completed.getStatus()).isEqualTo(BasecampStatus.COMPLETED);
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
