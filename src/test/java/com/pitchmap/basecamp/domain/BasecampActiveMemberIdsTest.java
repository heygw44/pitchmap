package com.pitchmap.basecamp.domain;

import static com.pitchmap.basecamp.domain.BasecampBuilder.LEADER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.MEMBER_ID;
import static com.pitchmap.basecamp.domain.BasecampBuilder.NOW;
import static com.pitchmap.basecamp.domain.BasecampBuilder.aBasecamp;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BasecampActiveMemberIdsTest {

    @Test
    @DisplayName("[F-14][BC-12] ACTIVE 멤버의 ID는 캠프 리더를 포함해 합류한 순서로 나오고, 대기 신청자는 들어가지 않는다")
    void activeMemberIdsIncludeLeaderAndSkipApplicants() {
        // given
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);

        // when
        var ids = basecamp.activeMemberIds();

        // then
        assertThat(ids).containsExactly(LEADER_ID, MEMBER_ID);
    }

    @Test
    @DisplayName("[F-14][BC-12] 탈퇴한 멤버의 ID는 ACTIVE 멤버 ID에서 빠진다")
    void leftMemberIsExcluded() {
        // given
        Basecamp basecamp = aBasecamp().inState(BasecampStatus.RECRUITING);
        basecamp.leave(MEMBER_ID, NOW);

        // when
        var ids = basecamp.activeMemberIds();

        // then
        assertThat(ids).containsExactly(LEADER_ID);
    }
}
