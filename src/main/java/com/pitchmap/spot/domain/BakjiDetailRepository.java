package com.pitchmap.spot.domain;

import java.util.Optional;

public interface BakjiDetailRepository {

    BakjiDetail save(BakjiDetail bakjiDetail);

    Optional<BakjiDetail> findById(Long spotId);
}
