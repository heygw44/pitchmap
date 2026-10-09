package com.pitchmap.trust.application;

import com.pitchmap.member.application.MemberSessions;
import com.pitchmap.member.application.MemberWithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴 유스케이스다. 비밀번호를 확인하고, 개인정보를 처리하고, 커밋한 뒤 그 회원의 세션을 모두 지운다.
 * 이메일과 비밀번호는 로그와 예외 메시지에 싣지 않는다.
 *
 * <p>이 클래스에는 {@code @Transactional}을 두지 않는다. 비밀번호 확인(BCrypt)은 트랜잭션 밖에서 해야 DB 커넥션을 오래 쥐지 않고,
 * DB 변경은 {@link WithdrawalApplier}의 트랜잭션이 맡는다. 세션 삭제는 그 트랜잭션이 커밋된 뒤에 한다. Spring Session JDBC는
 * 세션 작업마다 새 트랜잭션(두 번째 커넥션)을 열어서, 트랜잭션 안에서 세션을 지우면 동시 요청이 커넥션 풀 크기에 닿을 때
 * 서로 커넥션을 기다리며 멈춘다. 진행 중인 베이스캠프와 행사 신청 정리는 커밋된 이벤트를 발행기가 처리기에 넘겨서 하므로 기다리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final MemberWithdrawalService memberWithdrawalService;
    private final WithdrawalApplier withdrawalApplier;
    private final MemberSessions memberSessions;

    /**
     * 호출하면 비밀번호를 확인한 뒤 회원을 탈퇴 처리하고, 트랜잭션이 커밋된 뒤에 그 회원의 세션을 모두 지운다.
     * 비밀번호가 틀리면 {@code LOGIN_FAILED}로 거부하고 아무것도 바꾸지 않는다. 커밋 뒤 세션 삭제가 실패하면 탈퇴는 이미 끝난 상태이고
     * 예전 세션이 남을 수 있다.
     */
    public void withdraw(long memberId, String password) {
        memberWithdrawalService.verifyPassword(memberId, password);
        withdrawalApplier.apply(memberId);
        memberSessions.invalidateAll(memberId);
        log.info("member withdrawn memberId={}", memberId);
    }
}
