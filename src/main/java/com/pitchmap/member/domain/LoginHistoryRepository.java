package com.pitchmap.member.domain;

import java.time.Instant;
import java.util.List;

public interface LoginHistoryRepository {

    LoginHistory save(LoginHistory loginHistory);

    /**
     * 호출하면 그 이메일의 마지막 성공 이후 실패 시각 중 {@code after}보다 늦은 것을 오래된 순서로 돌려준다.
     * 성공 기록이 없으면 처음부터 센다. 성공 전후를 시각이 아니라 기록 순서(id)로 가르므로, 같은 시각에 쌓인 기록도 구분된다.
     */
    List<Instant> findFailureTimesSinceLastSuccess(String attemptedEmail, Instant after);
}
