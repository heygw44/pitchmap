package com.pitchmap.common.idempotency;

import java.io.Serializable;

/** {@link IdempotencyRecord}의 복합 기본 키. 요청한 회원 ID와 클라이언트가 만든 멱등성 키다. */
public record IdempotencyRecordId(long memberId, String idempotencyKey) implements Serializable {}
