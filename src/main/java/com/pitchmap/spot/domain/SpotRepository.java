package com.pitchmap.spot.domain;

import java.util.Optional;

public interface SpotRepository {

    Spot save(Spot spot);

    Optional<Spot> findById(Long id);
}
