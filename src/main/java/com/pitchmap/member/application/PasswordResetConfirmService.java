package com.pitchmap.member.application;

import com.pitchmap.member.domain.MemberException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 재설정 링크의 토큰으로 새 비밀번호를 정한다. 토큰 원값과 비밀번호는 로그와 예외 메시지에 싣지 않는다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. DB 변경은 {@link PasswordResetApplier}의 트랜잭션이 맡고,
 * 세션 삭제는 그 트랜잭션이 커밋된 뒤에 한다. Spring Session JDBC는 세션 작업마다 새 트랜잭션(두 번째 커넥션)을 열어서,
 * 트랜잭션 안에서 세션을 지우면 동시 요청이 커넥션 풀 크기에 닿을 때 서로 커넥션을 기다리며 멈춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetConfirmService {

    private final PasswordResetApplier passwordResetApplier;
    private final MemberSessions memberSessions;

    /**
     * 호출하면 토큰을 사용 처리하고 회원 비밀번호를 바꾼 뒤, 그 회원의 다른 토큰과 모든 세션을 무효로 만든다.
     * 세션은 새 비밀번호가 확정된 뒤에만 지우므로, 앞 단계가 실패하면 세션은 그대로 남는다.
     * 커밋 뒤 세션 삭제가 실패하면 비밀번호는 이미 바뀐 상태이고 예전 세션이 남을 수 있다.
     *
     * @throws MemberException 비밀번호가 규칙을 어기면 MEMBER_PASSWORD_POLICY(토큰은 그대로 쓸 수 있다),
     *     토큰이 형식에 맞지 않거나 없거나 만료됐거나 이미 사용됐으면 PASSWORD_RESET_TOKEN_INVALID
     */
    public void confirm(String rawToken, String newPassword) {
        long memberId = passwordResetApplier.apply(rawToken, newPassword);
        memberSessions.invalidateAll(memberId);
        log.info("password reset memberId={}", memberId);
    }
}
