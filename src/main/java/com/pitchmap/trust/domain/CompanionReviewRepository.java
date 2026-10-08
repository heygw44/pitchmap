package com.pitchmap.trust.domain;

import java.util.Optional;

public interface CompanionReviewRepository {

    CompanionReview saveAndFlush(CompanionReview review);

    Optional<CompanionReview> findById(Long id);
}
