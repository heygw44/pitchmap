package com.pitchmap.spot.application;

import java.util.List;

/**
 * 박지를 제보하거나 수정한 결과다. 서비스는 저장한 좌표로 공원 경계 경고와 50m 안의 기존 박지(중복 후보)를 함께 돌려준다.
 *
 * @param spotId 제보하거나 수정한 박지의 장소 ID
 * @param warned 좌표가 공원 경계 안이라 경고가 붙었으면 true
 * @param areaName 경고를 붙인 공원의 이름. 경고가 아니면 null
 * @param guide 제보자에게 보여 줄 안내 문구
 * @param duplicateCandidates 가까운 순서의 중복 후보. 없으면 빈 목록
 */
public record BakjiSubmission(
        long spotId, boolean warned, String areaName, String guide, List<BakjiDuplicateCandidate> duplicateCandidates) {

    public BakjiSubmission {
        duplicateCandidates = List.copyOf(duplicateCandidates);
    }
}
