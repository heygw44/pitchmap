package com.pitchmap.trust.domain;

import java.time.Instant;
import java.util.Optional;

public interface SanctionRepository {

    Sanction save(Sanction sanction);

    /**
     * 호출하면 그 회원의 확정 제재 중 가장 높은 단계를 돌려준다. 해제된 제재와 임시 정지는 넣지 않고, 적용 중인 제재와 기간이 끝난 제재는 넣는다.
     * 확정된 제재가 없으면 비어 있다.
     */
    Optional<Byte> findHighestConfirmedLevel(long memberId);

    /** 호출하면 그 회원에게 now 시점에 적용 중인 이용 정지가 있는지 알려 준다. 경고는 이용을 막지 않으므로 넣지 않는다. */
    boolean existsActiveSuspension(long memberId, Instant now);
}
