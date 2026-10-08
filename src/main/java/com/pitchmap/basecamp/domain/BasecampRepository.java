package com.pitchmap.basecamp.domain;

import java.util.Collection;
import java.util.Optional;

public interface BasecampRepository {

    Basecamp saveAndFlush(Basecamp basecamp);

    Optional<Basecamp> findById(Long id);

    /**
     * 호출하면 id인 베이스캠프를 쓰기 잠금으로 읽는다. 같은 베이스캠프를 바꾸려는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다.
     * 호출하는 트랜잭션 안에서 첫 조회여야 잠금을 얻은 뒤 다른 트랜잭션이 커밋한 내용이 보인다.
     */
    Optional<Basecamp> findByIdForUpdate(Long id);

    /** 호출하면 지금까지 바꾼 엔티티 내용을 바로 DB에 쓴다. 그래서 새로 추가한 신청이나 멤버의 ID를 커밋 전에 얻을 수 있다. */
    void flush();

    /** leaderId인 캠프 리더가 연 베이스캠프 중 상태가 statuses에 드는 것의 개수다. */
    long countByLeaderIdAndStatusIn(long leaderId, Collection<BasecampStatus> statuses);
}
