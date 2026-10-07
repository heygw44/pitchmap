package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampBuilder.APPLICANT_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.LEADER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.MEMBER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.NOW;
import static com.pitchmap.basecamp.domain.BasecampBuilder.OUTSIDER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.aBasecamp;
import static com.pitchmap.basecamp.domain.BasecampBuilder.applicationIdOf;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasecampRelationTest {

    @Test
    @DisplayName("[F-12] 비로그인 요청자와 관계없는 회원은 NONE이다")
    void anonymousAndOutsiderAreNone() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        assertThat(basecamp.relationOf(null)).isEqualTo(BasecampRelation.NONE);
        assertThat(basecamp.relationOf(OUTSIDER_ID)).isEqualTo(BasecampRelation.NONE);
    }

    @Test
    @DisplayName("[F-12] 캠프 리더는 LEADER, 승인된 멤버는 MEMBER, 대기 중인 신청자는 APPLICANT다")
    void leaderMemberAndApplicant() {
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        assertThat(basecamp.relationOf(LEADER_ID)).isEqualTo(BasecampRelation.LEADER);
        assertThat(basecamp.relationOf(MEMBER_ID)).isEqualTo(BasecampRelation.MEMBER);
        assertThat(basecamp.relationOf(APPLICANT_ID)).isEqualTo(BasecampRelation.APPLICANT);
    }

    @Test
    @DisplayName("[F-12] 탈퇴·강퇴된 멤버와 거절·취소된 신청자는 NONE이다")
    void leftKickedRejectedAndCanceledAreNone() {
        // given
        Basecamp basecamp = aBasecamp().capacity(6).openOnly();
        BasecampBuilder.join(basecamp, MEMBER_ID);
        BasecampBuilder.join(basecamp, APPLICANT_ID);
        BasecampBuilder.apply(basecamp, OUTSIDER_ID);
        basecamp.leave(MEMBER_ID, NOW);
        basecamp.kick(APPLICANT_ID, KickReason.NO_CONTACT, NOW);
        basecamp.reject(applicationIdOf(OUTSIDER_ID), NOW);

        // then
        assertThat(basecamp.relationOf(MEMBER_ID)).isEqualTo(BasecampRelation.NONE);
        assertThat(basecamp.relationOf(APPLICANT_ID)).isEqualTo(BasecampRelation.NONE);
        assertThat(basecamp.relationOf(OUTSIDER_ID)).isEqualTo(BasecampRelation.NONE);
    }

    @Test
    @DisplayName("[F-12] 신청을 스스로 취소한 회원은 NONE이다")
    void canceledApplicantIsNone() {
        // given
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
        basecamp.cancelApplication(APPLICANT_ID, NOW);

        // then
        assertThat(basecamp.relationOf(APPLICANT_ID)).isEqualTo(BasecampRelation.NONE);
    }
}
