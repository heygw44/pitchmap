package com.pitchmap.member.application;

import com.pitchmap.common.error.RateLimitedException;
import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.member.domain.Email;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberRepository;
import com.pitchmap.member.domain.MemberStatus;
import com.pitchmap.member.domain.PasswordResetRequestPolicy;
import com.pitchmap.member.domain.PasswordResetThrottle;
import com.pitchmap.member.domain.PasswordResetThrottleRepository;
import com.pitchmap.member.domain.PasswordResetToken;
import com.pitchmap.member.domain.PasswordResetTokenRepository;
import com.pitchmap.member.domain.ResetToken;
import com.pitchmap.member.infra.PasswordResetThrottleMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 요청 유스케이스: 요청 접수와 토큰 발급.
 *
 * <p>토큰 원값은 {@link #issueFor}가 돌려주는 {@link IssuedPasswordReset}에만 있다. DB에는 해시만 저장하고,
 * 로그와 예외 메시지와 이벤트 payload에는 토큰도 이메일도 싣지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetRequestService {

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OutboxEventRecorder outboxEventRecorder;
    private final PasswordResetThrottleRepository throttleRepository;
    private final PasswordResetThrottleMapper throttleMapper;
    private final Clock clock;

    // SecureRandom은 스레드에 안전하다. 토큰은 추측할 수 없어야 하므로 Random을 쓰지 않는다.
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 호출하면 요청 한도를 먼저 검사하고, 통과하면 그 이메일의 회원이 있고 탈퇴하지 않았을 때만 재설정 요청 이벤트를 기록한다.
     * 한도는 입력한 이메일 문자열과 요청 IP로만 정하고 회원 조회보다 먼저 판정한다. 그래서 가입 여부가 응답에 드러나지 않는다.
     * 가입 여부에 따라 달라지는 일은 이벤트 한 건 기록뿐이고, 토큰 발급과 메일 발송은 이벤트 처리기가 트랜잭션 밖에서 한다.
     *
     * @throws RateLimitedException 이메일이나 IP 한도에 걸렸을 때. 풀리기까지 남은 시간 중 긴 쪽을 담는다.
     */
    @Transactional
    public void request(String email, String requestIp) {
        requireRequestIp(requestIp);
        Instant now = Instant.now(clock);
        String normalized = Email.of(email).value();

        // 행을 잠그는 순서는 늘 IP 행이 먼저, 이메일 행이 나중이다. 요청마다 순서가 다르면 서로의 행을 기다리며 교착한다.
        // 행을 잠가야 같은 키의 동시 요청이 한 줄로 서서, 판정과 기록 사이에 다른 요청이 끼어들지 못한다.
        PasswordResetThrottle ipRow = lockRow(PasswordResetRequestPolicy.ipKey(requestIp), now);
        PasswordResetThrottle emailRow = lockRow(PasswordResetRequestPolicy.emailKey(normalized), now);

        Optional<Duration> blockedFor = longest(
                ipRow.blockedFor(PasswordResetRequestPolicy.IP, now),
                emailRow.blockedFor(PasswordResetRequestPolicy.EMAIL, now));
        if (blockedFor.isPresent()) {
            // 예외로 트랜잭션이 롤백되므로 한도에 걸린 요청은 어느 행의 횟수에도 들어가지 않는다.
            throw new RateLimitedException(MemberErrorCode.PASSWORD_RESET_LIMITED, blockedFor.get());
        }
        ipRow.record(PasswordResetRequestPolicy.IP, now);
        emailRow.record(PasswordResetRequestPolicy.EMAIL, now);

        memberRepository
                .findByEmail(normalized)
                .filter(PasswordResetRequestService::isTarget)
                .ifPresent(member -> outboxEventRecorder.record(
                        PasswordResetEvents.EVENT_TYPE,
                        PasswordResetEvents.AGGREGATE_TYPE,
                        member.getId(),
                        new PasswordResetEvents.Payload()));
    }

    // 행이 없으면 먼저 만든다. 이미 있어도 이 삽입이 행 잠금을 얻고, 첫 요청 둘이 동시에 와도 한 행만 생긴다.
    private PasswordResetThrottle lockRow(String key, Instant now) {
        throttleMapper.insertIfAbsent(key, now);
        return throttleRepository.findByKeyForUpdate(key).orElseThrow(PasswordResetRequestService::throttleRowMissing);
    }

    private static Optional<Duration> longest(Optional<Duration> first, Optional<Duration> second) {
        if (first.isPresent() && second.isPresent()) {
            return first.get().compareTo(second.get()) >= 0 ? first : second;
        }
        return first.isPresent() ? first : second;
    }

    private static IllegalStateException throttleRowMissing() {
        return new IllegalStateException("재설정 요청 한도 행을 만들었는데 읽을 수 없습니다.");
    }

    private static void requireRequestIp(String requestIp) {
        if (requestIp == null || requestIp.isBlank()) {
            throw new IllegalArgumentException("요청 IP가 비어 있습니다.");
        }
    }

    /**
     * 호출하면 새 재설정 토큰을 만들고 해시만 저장한 뒤, 토큰 원값과 받을 이메일을 돌려준다.
     * 회원이 없거나 탈퇴했으면 아무것도 저장하지 않고 빈 값을 돌려준다.
     * 이전 토큰은 지우지 않는다. 각 토큰은 발급 뒤 30분 동안 한 번만 쓸 수 있다.
     */
    @Transactional
    public Optional<IssuedPasswordReset> issueFor(long memberId) {
        return memberRepository
                .findById(memberId)
                .filter(PasswordResetRequestService::isTarget)
                .map(this::issue);
    }

    private IssuedPasswordReset issue(Member member) {
        ResetToken token = ResetToken.generate(secureRandom);
        passwordResetTokenRepository.save(
                PasswordResetToken.issue(member.getId(), ResetToken.hash(token.value()), Instant.now(clock)));
        return new IssuedPasswordReset(member.getEmail(), token.value());
    }

    // 탈퇴한 회원은 계정을 되살릴 수 없으므로 링크를 보내지 않는다. 그 밖의 상태는 비밀번호를 잊을 수 있다.
    private static boolean isTarget(Member member) {
        return member.getStatus() != MemberStatus.WITHDRAWN;
    }
}
