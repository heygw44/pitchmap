package com.pitchmap.notification.infra;

import java.time.Instant;

/**
 * 아직 발행하지 못한 이벤트의 현황. 가장 오래된 PENDING 이벤트가 없으면 {@code oldestPendingCreatedAt}은 null이다.
 * MyBatis가 이름으로 매핑하므로, 구성요소 이름은 SELECT의 별칭과 같아야 한다.
 */
public record OutboxBacklogRow(long pendingCount, long failedCount, Instant oldestPendingCreatedAt) {}
