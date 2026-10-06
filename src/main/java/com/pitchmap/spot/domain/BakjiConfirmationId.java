package com.pitchmap.spot.domain;

import java.io.Serializable;

/** {@link BakjiConfirmation}의 복합 기본 키. 박지(장소) ID와 확인한 회원 ID다. */
public record BakjiConfirmationId(long spotId, long memberId) implements Serializable {}
