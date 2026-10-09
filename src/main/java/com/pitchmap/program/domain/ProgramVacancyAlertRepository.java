package com.pitchmap.program.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProgramVacancyAlertRepository {

    /**
     * 호출하면 memberId인 회원의 빈자리 알림 신청을 만든다. 이미 신청했으면 아무것도 바꾸지 않는다.
     * 새로 만들었으면 1, 이미 있었으면 0을 돌려준다.
     */
    int subscribe(long programId, long memberId, Instant now);

    /** 호출하면 memberId인 회원의 빈자리 알림 신청을 지운다. 신청한 적이 없으면 아무것도 하지 않는다. */
    void unsubscribe(long programId, long memberId);

    /** 호출하면 programId인 행사의 알림 신청자 중 결제 대기·확정 신청이 없는 회원의 ID를 오름차순으로 돌려준다. */
    List<Long> findAlertTargets(long programId);

    /** 호출하면 programId인 행사에서 memberIds인 회원의 알림 시각을 now로 적는다. */
    void markNotified(long programId, Collection<Long> memberIds, Instant now);
}
