package com.pitchmap.member.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

/** 이메일 인증 코드의 유효 시간, 시도 횟수, 재발송 한도. 값은 이 클래스 한 곳에서만 정한다. */
public final class EmailVerificationPolicy {

    public static final Duration CODE_VALIDITY = Duration.ofMinutes(10);
    public static final int MAX_ATTEMPTS = 5;

    public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);

    /** 이메일당 한도. 회원 한 명이 이메일 하나를 가지므로 회원 기준으로 센다. 가입할 때 보낸 코드도 센다. */
    public static final Duration DAILY_WINDOW = Duration.ofHours(24);

    public static final int DAILY_LIMIT = 5;

    public static final Duration IP_WINDOW = Duration.ofHours(1);
    public static final int IP_LIMIT = 20;

    private EmailVerificationPolicy() {}

    /**
     * 재발송 판정에 필요한 발송 이력.
     *
     * @param lastSentAt 이 회원에게 가장 최근에 코드를 발송한 시각. 발송한 적이 없으면 null이다.
     * @param sentInDay 이 회원에게 최근 {@link #DAILY_WINDOW} 안에 발송한 횟수
     * @param oldestSentInDay 그 구간에서 가장 오래된 발송 시각. 횟수가 0이면 null이다.
     * @param sentByIpInHour 이 IP가 최근 {@link #IP_WINDOW} 안에 요청한 발송 횟수(회원과 무관)
     * @param oldestSentByIpInHour 그 구간에서 가장 오래된 발송 시각. 횟수가 0이면 null이다.
     * @param sendPending 이 회원의 발송 요청 중 아직 처리하지 않은 것이 있는지 여부
     */
    public record ResendHistory(
            Instant lastSentAt,
            int sentInDay,
            Instant oldestSentInDay,
            int sentByIpInHour,
            Instant oldestSentByIpInHour,
            boolean sendPending) {

        public ResendHistory {
            requireConsistent(sentInDay, oldestSentInDay);
            requireConsistent(sentByIpInHour, oldestSentByIpInHour);
        }

        private static void requireConsistent(int count, Instant oldest) {
            if (count < 0 || (count == 0) != (oldest == null)) {
                throw new IllegalArgumentException("발송 횟수와 가장 오래된 발송 시각이 서로 맞지 않습니다.");
            }
        }
    }

    /**
     * 호출하면 재발송이 막혀 있을 때 풀리기까지 남은 시간을 돌려준다. 막은 이유가 여럿이면 가장 긴 시간이고, 막혀 있지 않으면 비어 있다.
     *
     * <p>한도를 채운 구간에서는 가장 오래된 발송이 구간 밖으로 밀려나야 한 건이 비므로, 그 시각을 기준으로 남은 시간을 계산한다.
     * 한도를 넘겨 쌓인 경우(처리기가 같은 요청을 두 번 처리한 경우 등)에는 실제로 풀리는 시각보다 이를 수 있다.
     * 그때 호출한 쪽이 일찍 다시 요청하면 다시 막히므로 한도는 지켜진다.
     *
     * <p>대기 중인 발송 요청이 있으면 아직 메일이 나가지 않았어도 최소 {@link #RESEND_INTERVAL}은 막는다.
     * 처리기가 발송 이력을 남기기 전에 사용자가 다시 눌러도 발송 요청이 겹쳐 쌓이지 않게 하기 위해서다.
     */
    public static Optional<Duration> resendBlockedFor(ResendHistory history, Instant now) {
        return Stream.of(
                        intervalRemaining(history, now),
                        limitRemaining(history.sentInDay(), DAILY_LIMIT, history.oldestSentInDay(), DAILY_WINDOW, now),
                        limitRemaining(
                                history.sentByIpInHour(), IP_LIMIT, history.oldestSentByIpInHour(), IP_WINDOW, now),
                        pendingRemaining(history))
                .flatMap(Optional::stream)
                .filter(remaining -> remaining.isPositive())
                .max(Duration::compareTo);
    }

    private static Optional<Duration> intervalRemaining(ResendHistory history, Instant now) {
        return Optional.ofNullable(history.lastSentAt())
                .map(lastSentAt -> Duration.between(now, lastSentAt.plus(RESEND_INTERVAL)));
    }

    private static Optional<Duration> limitRemaining(
            int sent, int limit, Instant oldestSent, Duration window, Instant now) {
        if (sent < limit) {
            return Optional.empty();
        }
        return Optional.ofNullable(oldestSent).map(oldest -> Duration.between(now, oldest.plus(window)));
    }

    private static Optional<Duration> pendingRemaining(ResendHistory history) {
        if (!history.sendPending()) {
            return Optional.empty();
        }
        return Optional.of(RESEND_INTERVAL);
    }
}
