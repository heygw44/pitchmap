package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.CompanionReviewTag;
import java.time.Instant;
import java.util.List;

/** 다른 회원이 보는 받은 후기 한 건. 작성자와 개별 "다시 동행" 여부는 담지 않는다. */
public record PublicCompanionReview(List<CompanionReviewTag> tags, String comment, Instant createdAt) {}
