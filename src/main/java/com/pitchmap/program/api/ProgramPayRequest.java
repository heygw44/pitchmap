package com.pitchmap.program.api;

import jakarta.validation.constraints.NotNull;

/** 결제 요청 본문이다. 멱등성 기록이 같은 키의 재요청과 본문을 비교하므로 단순한 값만 둔다. */
public record ProgramPayRequest(@NotNull PaymentMethod method) {}
