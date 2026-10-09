package com.pitchmap.program.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProgramApplicationRepository {

    /**
     * 호출하면 id인 신청을 쓰기 잠금으로 읽는다. 같은 신청을 결제하거나 취소하려는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다.
     * 호출하는 트랜잭션 안에서 첫 조회여야 잠금을 얻은 뒤 다른 트랜잭션이 커밋한 내용이 보인다.
     */
    Optional<ProgramApplication> findByIdForUpdate(Long id);

    /**
     * 호출하면 programId인 행사의 결제 대기·확정 신청을 쓰기 잠금으로 읽어 ID 순서로 돌려준다.
     * 결제나 취소가 진행 중이면 그 트랜잭션이 끝날 때까지 기다리므로, 돌려받은 신청은 커밋된 최신 상태다.
     */
    List<ProgramApplication> findActiveByProgramForUpdate(long programId);

    /**
     * 호출하면 programId인 행사의 결제 대기·확정 신청을 모두 CANCELED로 바꾸고, 취소 사유를 reason, 취소 시각을 now로 적는다.
     * 바꾼 신청 수를 돌려준다. 호출하는 쪽은 먼저 행사 행을 잠가서 이 사이에 새 신청이 들어오지 못하게 해야 한다.
     * 이 쿼리로 바꾼 신청의 엔티티는 같은 트랜잭션에서 계속 쓰지 않는다.
     */
    int cancelActiveByProgram(long programId, ProgramCancelReason reason, Instant now);

    /** 호출하면 memberId인 회원의 결제 대기 신청 ID를 ID 순서로 돌려준다. 잠금은 잡지 않는다. */
    List<Long> findPendingPaymentIdsByMember(long memberId);

    /**
     * 호출하면 memberId인 회원의 확정 신청 중 아직 확인 요청 시각이 없는 신청에 확인 요청 시각을 now로 적고, 바꾼 신청 수를 돌려준다.
     * 이미 시각이 있는 신청은 건드리지 않으므로 다시 호출해도 처음 시각이 남는다. 이 쿼리로 바꾼 신청의 엔티티는 같은 트랜잭션에서 계속 쓰지 않는다.
     */
    int requestReviewForConfirmed(long memberId, Instant now);
}
