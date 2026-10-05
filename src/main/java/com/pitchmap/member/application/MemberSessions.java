package com.pitchmap.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** 회원의 로그인 세션을 한꺼번에 지운다. */
@Component
@RequiredArgsConstructor
public class MemberSessions {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    /**
     * 호출하면 그 회원의 세션을 모두 지운다. 세션의 principal 이름은 회원 ID 문자열이다.
     * 비밀번호를 바꾸거나 회원 행을 지울 때 부른다. 세션이 남으면 예전 쿠키가 한동안 로그인 상태로 통한다.
     *
     * <p>DB 트랜잭션 안에서 부르지 않는다. Spring Session JDBC가 세션 작업마다 새 트랜잭션(두 번째 커넥션)을 열어서,
     * 트랜잭션이 커넥션을 잡은 채 부르면 동시 요청이 풀 크기에 닿을 때 서로 기다리며 멈춘다.
     * 미인증 계정 정리 작업은 스레드 하나인 스케줄러가 부르므로 커넥션을 최대 두 개만 잡는다.
     */
    public void invalidateAll(long memberId) {
        sessionRepository.findByPrincipalName(String.valueOf(memberId)).keySet().forEach(sessionRepository::deleteById);
    }
}
