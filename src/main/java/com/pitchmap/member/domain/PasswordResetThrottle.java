package com.pitchmap.member.domain;

import com.pitchmap.member.domain.PasswordResetRequestPolicy.Rule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재설정 요청 한도 키(이메일 또는 IP) 하나의 요청 기록. 키는 원문이 아니라 해시다.
 * 운영에서는 행을 {@code PasswordResetThrottleMapper}가 만들고, 서비스는 행을 잠근 채 {@link #blockedFor}로 판정한 뒤
 * {@link #record}로 센다.
 */
@Entity
@Table(name = "password_reset_throttle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetThrottle {

    @Id
    @Column(name = "throttle_key")
    private String key;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "request_count")
    private int requestCount;

    @Column(name = "last_request_at")
    private Instant lastRequestAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private PasswordResetThrottle(String key, Instant now) {
        this.key = key;
        this.windowStart = now;
        this.requestCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 호출하면 아직 요청이 없는 기록을 돌려준다. 구간은 {@code now}에 시작한 것으로 본다. */
    public static PasswordResetThrottle open(String key, Instant now) {
        return new PasswordResetThrottle(key, now);
    }

    /**
     * 호출하면 이 요청을 막아야 하는지 알려 준다. 막아야 하면 다시 요청할 수 있을 때까지 남은 시간을, 아니면 빈 값을 돌려준다.
     * 남은 시간은 항상 0보다 크다. 풀리는 시각 정각에는 풀린 것으로 본다.
     * 간격과 한도가 모두 막으면 더 긴 쪽이다.
     */
    public Optional<Duration> blockedFor(Rule rule, Instant now) {
        return Stream.of(intervalRemaining(rule, now), limitRemaining(rule, now))
                .flatMap(Optional::stream)
                .max(Duration::compareTo);
    }

    /**
     * 호출하면 요청 한 번을 센다. 구간이 끝났으면 새 구간을 {@code now}에 열고 센다.
     * 막혀야 하는 요청을 세면 한도가 의미를 잃으므로, 막힌 상태에서 부르면 예외를 던진다.
     */
    public void record(Rule rule, Instant now) {
        if (blockedFor(rule, now).isPresent()) {
            throw new IllegalStateException("한도에 걸린 재설정 요청은 셀 수 없습니다.");
        }
        if (!now.isBefore(windowStart.plus(rule.window()))) {
            this.windowStart = now;
            this.requestCount = 0;
        }
        this.requestCount++;
        this.lastRequestAt = now;
        this.updatedAt = now;
    }

    private Optional<Duration> intervalRemaining(Rule rule, Instant now) {
        if (lastRequestAt == null || rule.minInterval().isZero()) {
            return Optional.empty();
        }
        Instant allowedAt = lastRequestAt.plus(rule.minInterval());
        if (!allowedAt.isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, allowedAt));
    }

    private Optional<Duration> limitRemaining(Rule rule, Instant now) {
        Instant windowEnd = windowStart.plus(rule.window());
        if (!now.isBefore(windowEnd) || requestCount < rule.maxRequests()) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, windowEnd));
    }

    @Override
    public String toString() {
        return "PasswordResetThrottle[requestCount=" + requestCount + "]";
    }
}
