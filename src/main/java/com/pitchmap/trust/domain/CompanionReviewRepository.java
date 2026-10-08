package com.pitchmap.trust.domain;

public interface CompanionReviewRepository {

    CompanionReview saveAndFlush(CompanionReview review);
}
