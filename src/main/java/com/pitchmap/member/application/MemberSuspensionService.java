package com.pitchmap.member.application;

import com.pitchmap.member.domain.Member;
import com.pitchmap.member.domain.MemberRepository;
import com.pitchmap.member.infra.MemberSuspensionMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈이 회원의 이용을 정지시키거나 정지를 풀 때 부르는 서비스다. 세션 삭제는 하지 않는다.
 * 세션 삭제는 DB 트랜잭션 밖에서 해야 해서, 이 서비스를 부른 쪽이 트랜잭션이 끝난 뒤에 {@link MemberSessions}로 한다.
 */
@Service
@RequiredArgsConstructor
public class MemberSuspensionService {

    private final MemberRepository memberRepository;
    private final MemberSuspensionMapper memberSuspensionMapper;
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

    /**
     * 호출하면 회원 행을 쓰기 잠금으로 읽고, 호출한 트랜잭션이 끝날 때까지 쥔다. 제재 기록을 넣기 전에 먼저 불러야 한다.
     * 제재 행을 넣으면 외래 키 때문에 회원 행에 읽기 잠금이 먼저 걸리는데, 같은 회원을 동시에 제재하면 서로 읽기 잠금을 쥔 채
     * 쓰기 잠금을 기다리다 데드락이 난다. 또 같은 회원의 제재를 한 줄로 세워서, 동시에 확정해도 같은 단계가 두 번 계산되지 않는다.
     * 회원이 없으면 호출하는 쪽의 검사 누락이라서 {@link IllegalStateException}을 던진다.
     */
    @Transactional
    public void lockForSanction(long memberId) {
        memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("제재할 회원이 없습니다. memberId=" + memberId));
    }

    /** 호출하면 그 회원을 종료 시각 없이 영구 정지한다. 호출한 쪽의 트랜잭션에 참여하고, 없으면 새로 연다. 탈퇴한 회원은 바꾸지 않는다. */
    @Transactional
    public void suspendPermanently(long memberId) {
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalStateException("정지할 회원이 없습니다. memberId=" + memberId));
        member.suspendPermanently(Instant.now(clock));
    }

    /**
     * 호출하면 그 회원의 정지 종료 시각이 지났을 때 정지를 풀고 true를 돌려준다. 회원 행을 쓰기 잠금으로 읽는다.
     * 정지 중이 아니거나 영구 정지이거나 아직 끝나지 않았으면 아무것도 바꾸지 않고 false를 돌려준다.
     */
    @Transactional
    public boolean releaseIfExpired(long memberId) {
        return memberRepository
                .findByIdForUpdate(memberId)
                .map(member -> member.releaseSuspensionIfExpired(Instant.now(clock)))
                .orElse(false);
    }

    /**
     * 호출하면 정지 종료 시각이 지난 회원의 정지를 모두 풀고 푼 회원 수를 돌려준다. 자기 트랜잭션으로 한 번에 처리한다.
     * 영구 정지는 종료 시각이 없어서 풀리지 않는다.
     */
    @Transactional
    public int releaseExpired(Instant now) {
        return memberSuspensionMapper.releaseExpired(now);
    }
}
