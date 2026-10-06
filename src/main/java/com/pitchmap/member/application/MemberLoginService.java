package com.pitchmap.member.application;

import com.pitchmap.common.error.RateLimitedException;
import com.pitchmap.member.domain.Email;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.domain.MemberRepository;
import com.pitchmap.member.domain.MemberStatus;
import com.pitchmap.member.domain.MemberSuspendedException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인 유스케이스. 이 클래스에는 일부러 {@code @Transactional}을 걸지 않는다.
 * 실패하면 예외를 던지는데, 트랜잭션 안에서 던지면 실패 기록이 롤백되기 때문이다(자세한 이유는 {@link LoginAttemptService}).
 * 기록을 읽고 쓰는 일은 {@link LoginAttemptService}가 메서드마다 따로 커밋한다.
 */
@Service
public class MemberLoginService {

    private static final int ATTEMPTED_EMAIL_MAX_LENGTH = 254;

    private final MemberRepository memberRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;

    // 가입하지 않은 이메일로 시도해도 비밀번호 비교를 한 번 한다. 그렇지 않으면 BCrypt를 건너뛴 만큼 응답이 빨라져서,
    // 응답 시간만 재도 가입 여부를 알 수 있다. 해시는 주입받은 인코더로 직접 만들어 해시 강도를 실제 회원 해시와 맞춘다.
    private final String dummyPasswordHash;

    public MemberLoginService(
            MemberRepository memberRepository,
            LoginAttemptService loginAttemptService,
            PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.loginAttemptService = loginAttemptService;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResult login(LoginCommand command) {
        String attemptedEmail = normalizeEmail(command.email());
        rejectIfLocked(attemptedEmail);
        Member member = authenticate(attemptedEmail, command);
        rejectIfSuspended(member);
        loginAttemptService.recordSuccess(member.getId(), attemptedEmail, command.ip());
        return new LoginResult(member.getId(), member.getNickname(), member.getStatus(), member.getRole());
    }

    // 이메일 형식이 올바르지 않아도 500이 아니라 일반 로그인 실패로 다룬다. 그래서 형식 검사에서 거부된 값은
    // 소문자로 바꾸고 칼럼 길이에 맞춰 잘라서 실패 기록의 이메일로 쓴다.
    private static String normalizeEmail(String rawEmail) {
        try {
            return Email.of(rawEmail).value();
        } catch (IllegalArgumentException e) {
            String lowerCased = rawEmail.toLowerCase(Locale.ROOT);
            return lowerCased.substring(0, Math.min(lowerCased.length(), ATTEMPTED_EMAIL_MAX_LENGTH));
        }
    }

    // 잠긴 동안의 시도는 비밀번호를 확인하지 않고 기록하지도 않는다. 기록하면 실패 구간이 계속 뒤로 밀려서 잠금이 끝나지 않는다.
    private void rejectIfLocked(String attemptedEmail) {
        Optional<Duration> retryAfter = loginAttemptService.findRetryAfter(attemptedEmail);
        if (retryAfter.isPresent()) {
            throw new RateLimitedException(MemberErrorCode.LOGIN_LOCKED, retryAfter.get());
        }
    }

    private Member authenticate(String attemptedEmail, LoginCommand command) {
        Member member = memberRepository
                .findByEmail(attemptedEmail)
                .filter(found -> found.getStatus() != MemberStatus.WITHDRAWN)
                .orElse(null);
        String passwordHash = member != null ? member.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatched = passwordEncoder.matches(command.password(), passwordHash);
        if (member == null || !passwordMatched) {
            Long memberId = member != null ? member.getId() : null;
            loginAttemptService.recordFailure(memberId, attemptedEmail, command.ip());
            throw new MemberException(MemberErrorCode.LOGIN_FAILED);
        }
        return member;
    }

    // 정지 여부는 비밀번호가 맞은 뒤에 알린다. 비밀번호를 모르는 사람에게 계정이 정지됐다는 사실을 알리지 않기 위해서다.
    // 정지로 거부한 시도는 실패로 세지 않는다. 비밀번호는 맞았기 때문이다.
    private void rejectIfSuspended(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new MemberSuspendedException(member.getSuspendedUntil());
        }
    }
}
