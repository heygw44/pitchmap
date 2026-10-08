package com.pitchmap.trust.application;

import com.pitchmap.member.application.MemberSuspensionService;
import com.pitchmap.trust.infra.SanctionExpiryMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 기간이 끝난 제재를 만료로 바꾸고, 정지 기간이 끝난 회원의 정지를 푼다.
 *
 * <p>제재 만료는 UPDATE 한 문장이라 자체로 커밋되고, 회원 정지 해제는 {@link MemberSuspensionService}가 자기 트랜잭션으로 처리한다. 두 일을 따로 판단해도 맞는 이유는, 회원의
 * 정지 종료 시각이 그 회원에게 적용 중인 정지 중 가장 늦은 종료 시각이기 때문이다. 그 시각이 지났으면 적용 중인 정지가 없다.
 * 영구 정지는 종료 시각이 없어서 풀리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionExpiryService {

    private final SanctionExpiryMapper sanctionExpiryMapper;
    private final MemberSuspensionService memberSuspensionService;
    private final Clock clock;

    /** 호출하면 기간이 끝난 제재를 만료로 바꾸고, 이어서 정지 기간이 끝난 회원의 정지를 푼다. 바꾼 건수를 돌려준다. */
    public Result run() {
        Instant now = clock.instant();
        int expiredSanctions = sanctionExpiryMapper.expireEnded(now);
        int releasedMembers = memberSuspensionService.releaseExpired(now);
        log.info("sanction expiry expiredSanctions={} releasedMembers={}", expiredSanctions, releasedMembers);
        return new Result(expiredSanctions, releasedMembers);
    }

    /**
     * @param expiredSanctions 이번 실행에서 만료로 바꾼 제재 수
     * @param releasedMembers 이번 실행에서 정지를 푼 회원 수
     */
    public record Result(int expiredSanctions, int releasedMembers) {}
}
