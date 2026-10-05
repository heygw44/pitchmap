package com.pitchmap.member.application;

import com.pitchmap.common.outbox.OutboxEventRecorder;
import com.pitchmap.member.domain.Email;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberRepository;
import com.pitchmap.member.domain.MemberStatus;
import com.pitchmap.member.domain.PasswordResetToken;
import com.pitchmap.member.domain.PasswordResetTokenRepository;
import com.pitchmap.member.domain.ResetToken;
import java.security.SecureRandom;
import java.time.Clock;
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
    private final Clock clock;

    // SecureRandom은 스레드에 안전하다. 토큰은 추측할 수 없어야 하므로 Random을 쓰지 않는다.
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 호출하면 그 이메일의 회원이 있고 탈퇴하지 않았을 때만 재설정 요청 이벤트를 기록한다. 반환값이 없어서 호출한 쪽은 가입 여부를 알 수 없다.
     * 가입 여부에 따라 달라지는 일은 이벤트 한 건 기록뿐이고, 토큰 발급과 메일 발송은 이벤트 처리기가 트랜잭션 밖에서 한다.
     */
    @Transactional
    public void request(String email) {
        memberRepository
                .findByEmail(Email.of(email).value())
                .filter(PasswordResetRequestService::isTarget)
                .ifPresent(member -> outboxEventRecorder.record(
                        PasswordResetEvents.EVENT_TYPE,
                        PasswordResetEvents.AGGREGATE_TYPE,
                        member.getId(),
                        new PasswordResetEvents.Payload()));
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
