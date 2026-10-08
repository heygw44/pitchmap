package com.pitchmap.member.application;

import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈이 회원의 이용을 정지시킬 때 부르는 서비스다. 세션 삭제는 하지 않는다.
 * 세션 삭제는 DB 트랜잭션 밖에서 해야 해서, 이 서비스를 부른 쪽이 트랜잭션이 끝난 뒤에 {@link MemberSessions}로 한다.
 */
@Service
@RequiredArgsConstructor
public class MemberSuspensionService {

    private final MemberRepository memberRepository;
    private final Clock clock;

    /**
     * 호출하면 그 회원을 until까지 정지한다. 호출한 쪽의 트랜잭션에 참여하고, 없으면 새로 연다.
     * 회원 행을 쓰기 잠금으로 읽으므로, 같은 회원을 동시에 정지해도 순서대로 처리되어 가장 늦은 종료 시각이 남는다.
     * 탈퇴한 회원, 영구 정지된 회원, 더 늦게 끝나는 정지 중인 회원은 바꾸지 않는다.
     */
    @Transactional
    public void suspendTemporarily(long memberId, Instant until) {
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("정지할 회원이 없습니다. memberId=" + memberId));
        member.suspendTemporarily(until, Instant.now(clock));
    }
}
