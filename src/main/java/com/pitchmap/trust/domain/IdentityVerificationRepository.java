package com.pitchmap.trust.domain;

import java.util.Optional;

public interface IdentityVerificationRepository {

    IdentityVerification saveAndFlush(IdentityVerification identityVerification);

    Optional<IdentityVerification> findByMemberId(long memberId);

    boolean existsByMemberId(long memberId);

    boolean existsByCiHash(CiHash ciHash);
}
