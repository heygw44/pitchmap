package com.pitchmap.member.domain;

import java.time.Instant;
import java.util.Map;

public class MemberSuspendedException extends MemberException {

    private static final String SUSPENDED_UNTIL_FIELD = "suspendedUntil";

    private final Instant suspendedUntil;

    /** @param suspendedUntil 정지가 풀리는 시각. 영구 정지처럼 해제 시각이 없으면 null이다. */
    public MemberSuspendedException(Instant suspendedUntil) {
        super(MemberErrorCode.MEMBER_SUSPENDED);
        this.suspendedUntil = suspendedUntil;
    }

    // 영구 정지는 해제 시각이 없다. 그래서 null 값을 내보내지 않고 응답에서 키 자체를 뺀다.
    @Override
    public Map<String, Object> extraFields() {
        if (suspendedUntil == null) {
            return Map.of();
        }
        return Map.of(SUSPENDED_UNTIL_FIELD, suspendedUntil);
    }
}
