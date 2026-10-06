package com.pitchmap.spot.domain;

import java.util.Optional;

public interface SpotRepository {

    Spot save(Spot spot);

    Optional<Spot> findById(Long id);

    /** 장소 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 같은 행을 잠그려는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다. */
    Optional<Spot> findByIdForUpdate(long id);
}
