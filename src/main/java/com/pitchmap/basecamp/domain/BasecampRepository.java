package com.pitchmap.basecamp.domain;

import java.util.Collection;
import java.util.Optional;

public interface BasecampRepository {

    Basecamp saveAndFlush(Basecamp basecamp);

    Optional<Basecamp> findById(Long id);

    /** leaderId인 캠프 리더가 연 베이스캠프 중 상태가 statuses에 드는 것의 개수다. */
    long countByLeaderIdAndStatusIn(long leaderId, Collection<BasecampStatus> statuses);
}
