package com.pitchmap.member.application;

import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.domain.MemberRepository;
import com.pitchmap.member.domain.Password;
import com.pitchmap.member.domain.PasswordResetToken;
import com.pitchmap.member.domain.PasswordResetTokenRepository;
import com.pitchmap.member.domain.ResetToken;
import com.pitchmap.member.infra.PasswordResetTokenMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정에서 트랜잭션 하나로 묶는 부분이다. 토큰 사용 처리, 비밀번호 변경, 다른 토큰 무효화를 함께 커밋하거나 함께 되돌린다.
 * 세션 삭제는 {@link PasswordResetConfirmService}가 이 트랜잭션이 끝난 뒤에 한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetApplier {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetTokenMapper passwordResetTokenMapper;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 호출하면 토큰을 사용 처리하고 회원 비밀번호를 바꾼 뒤, 그 회원의 사용하지 않은 다른 토큰을 무효로 만들고 회원 ID를 돌려준다.
     * 예외가 트랜잭션 밖으로 나가면 기본 설정대로 롤백하므로, 앞 단계가 한 일도 함께 사라진다.
     *
     * @throws MemberException 비밀번호가 규칙을 어기면 MEMBER_PASSWORD_POLICY(토큰은 그대로 쓸 수 있다),
     *     토큰이 형식에 맞지 않거나 없거나 만료됐거나 이미 사용됐으면 PASSWORD_RESET_TOKEN_INVALID
     */
    @Transactional
    public long apply(String rawToken, String newPassword) {
        // 비밀번호를 먼저 검사한다. 규칙을 어긴 입력으로 토큰이 소모되면 사용자가 메일을 다시 받아야 한다.
        Password password = Password.of(newPassword);
        Instant now = Instant.now(clock);
        PasswordResetToken token = findUsableToken(rawToken, now);
        // 같은 토큰을 동시에 쓴 요청 중 이 조건부 갱신이 1행을 돌려받은 쪽만 이긴다.
        if (passwordResetTokenMapper.markUsed(token.getId(), now) == 0) {
            throw invalidToken();
        }
        long memberId = token.getMemberId();
        Member member = memberRepository.findById(memberId).orElseThrow(() -> memberNotFound(memberId));
        member.changePassword(passwordEncoder.encode(password.value()), now);
        passwordResetTokenMapper.invalidateUnusedTokens(memberId, now);
        return memberId;
    }

    // 없는 토큰, 만료된 토큰, 사용한 토큰이 모두 같은 오류를 받아야 응답으로 어느 쪽인지 알 수 없다.
    private PasswordResetToken findUsableToken(String rawToken, Instant now) {
        if (!ResetToken.isWellFormed(rawToken)) {
            throw invalidToken();
        }
        return passwordResetTokenRepository
                .findByTokenHash(ResetToken.hash(rawToken))
                .filter(found -> found.isUsable(now))
                .orElseThrow(PasswordResetApplier::invalidToken);
    }

    private static MemberException invalidToken() {
        return new MemberException(MemberErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }

    // 토큰 행은 회원 행이 지워지면 외래 키로 함께 지워지므로 일어나기 어렵다. 회원 ID만 메시지에 싣는다.
    private static IllegalStateException memberNotFound(long memberId) {
        return new IllegalStateException("재설정 토큰의 회원이 없습니다. memberId=" + memberId);
    }
}
