package com.pitchmap.spot.api;

import com.pitchmap.spot.application.BakjiSubmission;
import java.util.List;

/** 박지를 제보하거나 수정한 결과. 공원 경계 경고, 안내 문구, 50m 안의 기존 박지(중복 후보)를 함께 준다. */
public record BakjiSubmissionResponse(
        long spotId,
        ParkWarningResponse parkWarning,
        String guide,
        List<DuplicateCandidateResponse> duplicateCandidates) {

    static BakjiSubmissionResponse from(BakjiSubmission submission) {
        return new BakjiSubmissionResponse(
                submission.spotId(),
                new ParkWarningResponse(submission.warned(), submission.areaName()),
                submission.guide(),
                submission.duplicateCandidates().stream()
                        .map(DuplicateCandidateResponse::from)
                        .toList());
    }
}
