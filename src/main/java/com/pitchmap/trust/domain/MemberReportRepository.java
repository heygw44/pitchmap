package com.pitchmap.trust.domain;

import java.util.Optional;

public interface MemberReportRepository {

    MemberReport saveAndFlush(MemberReport report);

    Optional<MemberReport> findById(Long id);

    /** 호출하면 그 신고 행을 쓰기 잠금으로 읽는다. 잠금은 호출한 트랜잭션이 끝날 때까지 유지되어 같은 신고의 동시 처리를 한 줄로 세운다. */
    Optional<MemberReport> findByIdForUpdate(long id);
}
