package com.pitchmap.member.domain;

import java.util.Optional;

public interface PasswordResetThrottleRepository {

    /**
     * 호출하면 그 키의 행을 쓰기 잠금으로 읽는다. 같은 키를 동시에 판정하는 요청이 한 번에 하나씩만 지나가게 하려는 것이다.
     * 트랜잭션 안에서만 호출할 수 있다. 행이 없으면 빈 값이다.
     */
    Optional<PasswordResetThrottle> findByKeyForUpdate(String key);
}
