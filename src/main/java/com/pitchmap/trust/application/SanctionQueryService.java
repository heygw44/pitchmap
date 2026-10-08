package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.SanctionRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원의 제재 상태를 다른 모듈에 알려 준다. */
@Service
@RequiredArgsConstructor
public class SanctionQueryService {

    private final SanctionRepository sanctionRepository;
    private final Clock clock;

    /**
     * 호출하면 그 회원에게 지금 적용 중인 이용 정지가 있는지 알려 준다. 경고와 기간이 끝난 정지, 해제된 정지는 넣지 않는다.
     * 정지를 확정하면 세션을 바로 지우지만, 지우기 전에 이미 들어온 요청을 막으려고 서비스가 이 값으로 한 번 더 확인한다.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveSuspension(long memberId) {
        return sanctionRepository.existsActiveSuspension(memberId, clock.instant());
    }
}
