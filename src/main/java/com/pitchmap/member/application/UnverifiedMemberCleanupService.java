package com.pitchmap.member.application;

import com.pitchmap.member.domain.UnverifiedMemberPolicy;
import com.pitchmap.member.infra.UnverifiedMemberMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입한 지 7일이 지나도록 이메일 인증을 마치지 않은 계정을 지운다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnverifiedMemberCleanupService {

    // 한 번에 지우는 회원 수의 상한이다. 쌓인 계정이 많을 때 IN 목록과 한 번의 삭제가 너무 커지지 않게 나눈다.
    static final int BATCH_SIZE = 500;

    private final UnverifiedMemberMapper unverifiedMemberMapper;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final Clock clock;

    /**
     * 호출하면 삭제 대상 회원을 BATCH_SIZE개씩 지우기를 대상이 없어질 때까지 되풀이하고, 지운 회원 수를 돌려준다.
     * 인증 코드는 외래 키가 함께 지우고, 접속 기록은 회원 ID만 비우고 남긴다.
     */
    @Transactional
    public int deleteExpiredUnverifiedMembers() {
        Instant cutoff = UnverifiedMemberPolicy.deletionCutoff(Instant.now(clock));
        int deletedTotal = 0;
        List<Long> expiredIds = unverifiedMemberMapper.selectExpiredIds(cutoff, BATCH_SIZE);
        while (!expiredIds.isEmpty()) {
            deleteSessionsOf(expiredIds);
            int deleted = unverifiedMemberMapper.deleteExpiredByIds(expiredIds, cutoff);
            deletedTotal += deleted;
            // 조회한 시점 이후에 인증을 마친 회원은 삭제 문장이 건너뛴다. 그런 회원만으로 한 묶음이 찼다면
            // 다음 조회가 같은 회원을 다시 돌려주므로, 하나도 지우지 못한 묶음에서 멈춘다.
            if (deleted == 0 || expiredIds.size() < BATCH_SIZE) {
                break;
            }
            expiredIds = unverifiedMemberMapper.selectExpiredIds(cutoff, BATCH_SIZE);
        }
        log.info("unverified members deleted count={}", deletedTotal);
        return deletedTotal;
    }

    // 회원 행만 지우면 세션이 남아서, 삭제된 회원의 쿠키가 한동안 로그인 상태로 통한다.
    // 세션의 principal 이름은 회원 ID 문자열이다.
    private void deleteSessionsOf(List<Long> memberIds) {
        for (Long memberId : memberIds) {
            sessionRepository
                    .findByPrincipalName(String.valueOf(memberId))
                    .keySet()
                    .forEach(sessionRepository::deleteById);
        }
    }
}
