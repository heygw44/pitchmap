package com.pitchmap.trust.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SanctionRepository {

    Sanction save(Sanction sanction);

    /**
     * 호출하면 그 회원의 확정 제재 중 가장 높은 단계를 돌려준다. 해제된 제재와 임시 정지는 넣지 않고, 적용 중인 제재와 기간이 끝난 제재는 넣는다.
     * 확정된 제재가 없으면 비어 있다.
     */
    Optional<Byte> findHighestConfirmedLevel(long memberId);

    /** 호출하면 그 회원에게 now 시점에 적용 중인 이용 정지가 있는지 알려 준다. 경고는 이용을 막지 않으므로 넣지 않는다. */
    boolean existsActiveSuspension(long memberId, Instant now);

    /** 호출하면 그 회원에게 status가 아닌 제재가 하나라도 있는지 알려 준다. 해제되지 않은 제재 이력을 찾을 때 LIFTED를 넘긴다. */
    boolean existsByMemberIdAndStatusNot(long memberId, SanctionStatus status);

    Optional<Sanction> findById(Long id);

    /** 호출하면 제재 엔티티를 읽지 않고 그 제재를 받은 회원 ID만 돌려준다. 잠금을 쥐기 전에 엔티티를 읽어 오래된 상태가 남는 일을 피하려는 것이다. */
    Optional<Long> findMemberIdById(long sanctionId);

    /** 호출하면 그 제재 행을 쓰기 잠금으로 읽는다. 잠금은 호출한 트랜잭션이 끝날 때까지 유지된다. */
    Optional<Sanction> findByIdForUpdate(long id);

    /** 호출하면 그 회원에게 적용 중(ACTIVE)인 정지 제재를 모두 돌려준다. 경고는 넣지 않고, 종료 시각이 지났어도 아직 만료 처리 전이면 넣는다. */
    List<Sanction> findActiveSuspensions(long memberId);

    /** 호출하면 그 신고가 만든 적용 중인 임시 정지(72시간)를 모두 돌려준다. 긴급 신고를 접수하면 하나가 생긴다. */
    List<Sanction> findActiveTemporaryByReportId(long reportId);
}
