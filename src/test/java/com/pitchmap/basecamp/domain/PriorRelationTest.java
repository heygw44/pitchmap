package com.pitchmap.basecamp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriorRelationTest {

    @Test
    @DisplayName("[F-12][BC-07] ACTIVE 멤버나 대기 신청은 ACTIVE로 본다")
    void activeMemberAndPendingApplicationAreActive() {
        assertThat(PriorRelation.of(BasecampMemberStatus.ACTIVE, BasecampApplicationStatus.APPROVED))
                .isEqualTo(PriorRelation.ACTIVE);
        assertThat(PriorRelation.of(BasecampMemberStatus.ACTIVE, null)).isEqualTo(PriorRelation.ACTIVE);
        assertThat(PriorRelation.of(null, BasecampApplicationStatus.PENDING)).isEqualTo(PriorRelation.ACTIVE);
    }

    @Test
    @DisplayName("[F-12][BC-07] 탈퇴·강퇴된 멤버와 거절된 신청은 BLOCKED로 본다. 승인된 신청의 멤버가 탈퇴해도 BLOCKED다")
    void leftKickedAndRejectedAreBlocked() {
        assertThat(PriorRelation.of(BasecampMemberStatus.LEFT, BasecampApplicationStatus.APPROVED))
                .isEqualTo(PriorRelation.BLOCKED);
        assertThat(PriorRelation.of(BasecampMemberStatus.KICKED, BasecampApplicationStatus.APPROVED))
                .isEqualTo(PriorRelation.BLOCKED);
        assertThat(PriorRelation.of(null, BasecampApplicationStatus.REJECTED)).isEqualTo(PriorRelation.BLOCKED);
    }

    @Test
    @DisplayName("[F-12][BC-07] 관계가 없거나 신청을 스스로 취소했거나 신청이 만료됐으면 NONE이다")
    void canceledExpiredAndNoRelationAreNone() {
        assertThat(PriorRelation.of(null, null)).isEqualTo(PriorRelation.NONE);
        assertThat(PriorRelation.of(null, BasecampApplicationStatus.CANCELED)).isEqualTo(PriorRelation.NONE);
        assertThat(PriorRelation.of(null, BasecampApplicationStatus.EXPIRED)).isEqualTo(PriorRelation.NONE);
    }
}
