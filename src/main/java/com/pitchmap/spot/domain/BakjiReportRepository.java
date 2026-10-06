package com.pitchmap.spot.domain;

public interface BakjiReportRepository {

    BakjiReport saveAndFlush(BakjiReport report);

    boolean existsBySpotIdAndReporterId(long spotId, long reporterId);

    long countBySpotId(long spotId);
}
