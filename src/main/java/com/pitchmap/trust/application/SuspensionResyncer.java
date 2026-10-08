package com.pitchmap.trust.application;

import com.pitchmap.member.application.MemberSuspensionService;
import com.pitchmap.trust.domain.Sanction;
import com.pitchmap.trust.domain.SanctionRepository;
import com.pitchmap.trust.domain.SanctionType;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 제재를 해제하거나 기각한 뒤, 그 회원에게 남은 적용 중인 정지를 읽어 회원의 정지 상태를 다시 맞춘다.
 *
 * <p>트랜잭션을 열지 않는다. 제재를 바꾼 호출자의 트랜잭션 안에서 불러야 하고, 회원 행 잠금은 호출자가 이미 쥐고 있어야 한다.
 */
@Component
@RequiredArgsConstructor
class SuspensionResyncer {

    private final SanctionRepository sanctionRepository;
    private final MemberSuspensionService memberSuspensionService;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 적용 중인 정지를 모두 읽어 영구 정지가 있는지와 가장 늦게 끝나는 시각을 구하고, 회원에게 반영한다.
     * 종료 시각이 이미 지났지만 아직 만료 처리가 안 된 정지는 남은 정지로 세지 않는다.
     */
    void resync(long memberId) {
        Instant now = clock.instant();
        List<Sanction> active = sanctionRepository.findActiveSuspensions(memberId);
        boolean permanent = active.stream().anyMatch(sanction -> sanction.getType() == SanctionType.PERMANENT);
        Instant latestEnd = active.stream()
                .map(Sanction::getEndsAt)
                .filter(Objects::nonNull)
                .filter(endsAt -> endsAt.isAfter(now))
                .max(Comparator.naturalOrder())
                .orElse(null);
        memberSuspensionService.resyncSuspension(memberId, permanent, latestEnd);
    }
}
