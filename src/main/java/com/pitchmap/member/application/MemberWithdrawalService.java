package com.pitchmap.member.application;

import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberErrorCode;
import com.pitchmap.member.domain.MemberException;
import com.pitchmap.member.domain.MemberRepository;
import com.pitchmap.member.domain.MemberStatus;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈이 회원 탈퇴를 처리할 때 부르는 서비스다. 세션 삭제는 하지 않는다.
 * 세션 삭제는 DB 트랜잭션 밖에서 해야 해서, 이 서비스를 부른 쪽이 트랜잭션이 끝난 뒤에 {@link MemberSessions}로 한다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 비밀번호가 password와 맞는지 확인하고, 틀리면 {@code LOGIN_FAILED}로 거부한다.
     * 회원이 없거나 이미 탈퇴했거나 비밀번호 해시가 없을 때도 같은 예외를 던져서 이유를 구분해 알리지 않는다.
     *
     * <p>이 메서드에는 {@code @Transactional}을 두지 않는다. BCrypt 비교가 느려서 트랜잭션 안에서 하면 DB 커넥션을 오래 쥐기 때문이다.
     * 로그인 기록을 남기거나 로그인 잠금 계산에 넣지도 않는다. 이미 로그인한 회원이 비밀번호를 다시 확인하는 것이라서,
     * 세션을 훔친 사람이 비밀번호를 추측해도 로그인 잠금이 걸리지 않는다는 점은 알고 쓴다.
     */
    public void verifyPassword(long memberId, String password) {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null
                || member.getStatus() == MemberStatus.WITHDRAWN
                || member.getPasswordHash() == null
                || !passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new MemberException(MemberErrorCode.LOGIN_FAILED);
        }
    }

    /**
     * 호출하면 memberId인 회원의 행을 쓰기 잠금으로 읽고 탈퇴 처리한다. 반드시 호출한 쪽의 트랜잭션 안에서 불러야 하고,
     * 트랜잭션이 없으면 예외가 난다. 같은 트랜잭션에서 제재 확정과 같은 순서로 회원 행을 가장 먼저 잠그라는 뜻이다.
     * 회원이 없거나 이미 탈퇴했으면 {@code LOGIN_FAILED}로 거부한다. 이 경우 두 탈퇴 요청이 겹쳤을 때 늦은 쪽이 이미 끝난 탈퇴를 다시 하지 않게 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void withdraw(long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId).orElse(null);
        if (member == null || member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new MemberException(MemberErrorCode.LOGIN_FAILED);
        }
        member.withdraw(clock.instant());
    }
}
