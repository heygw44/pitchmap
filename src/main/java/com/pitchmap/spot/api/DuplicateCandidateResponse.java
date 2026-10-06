package com.pitchmap.spot.api;

import com.pitchmap.spot.application.BakjiDuplicateCandidate;

/** 제보한 박지와 가까운 기존 박지. distanceM은 두 박지 사이의 거리(m)를 정수로 반올림한 값이다. */
public record DuplicateCandidateResponse(long spotId, String name, int distanceM) {

    static DuplicateCandidateResponse from(BakjiDuplicateCandidate candidate) {
        return new DuplicateCandidateResponse(candidate.spotId(), candidate.name(), candidate.distanceM());
    }
}
