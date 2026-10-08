package com.pitchmap.trust.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 동행 후기를 쓸 수 있는 기한과 받은 후기가 공개되는 시점을 정한다.
 *
 * <p>후기는 베이스캠프가 완료된 시각부터 14일 동안 쓸 수 있고, 14일이 되는 시각까지 포함한다. 받은 후기는 내가 그 상대에게 같은 베이스캠프의
 * 후기를 썼거나 작성 기한이 지나면 볼 수 있다. 상대의 후기를 본 뒤에 보복하듯 쓰는 일을 막으려는 규칙이다.
 */
public final class CompanionReviewPolicy {

    public static final Duration WRITE_PERIOD = Duration.ofDays(14);

    /** 후기를 쓰거나 작성할 후기를 보려면 필요한 신뢰 단계다. 본인확인을 마친 단계 1부터다. */
    public static final int MIN_TRUST_LEVEL = 1;

    private CompanionReviewPolicy() {}

    /** 호출하면 완료 시각 completedAt인 베이스캠프의 작성 기한을 돌려준다. 기한이 되는 시각까지 쓸 수 있다. */
    public static Instant deadlineOf(Instant completedAt) {
        return completedAt.plus(WRITE_PERIOD);
    }

    /** now에 후기를 쓸 수 있으면 true다. 기한이 되는 시각 정각에도 true다. */
    public static boolean isWritable(Instant completedAt, Instant now) {
        return !now.isAfter(deadlineOf(completedAt));
    }

    /** 호출하면 now에 작성 기한이 이미 지난 베이스캠프인지 가르는 완료 시각의 기준을 돌려준다. 이 시각보다 먼저 완료된 베이스캠프만 기한이 지났다. */
    public static Instant revealCutoff(Instant now) {
        return now.minus(WRITE_PERIOD);
    }

    /** 반대 방향 후기가 있거나 작성 기한이 지났으면 받은 후기를 공개한다. */
    public static boolean isRevealed(boolean reverseWritten, Instant completedAt, Instant now) {
        return reverseWritten || !isWritable(completedAt, now);
    }
}
