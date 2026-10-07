package com.pitchmap.spot.application;

import java.util.List;

/**
 * 박지를 제보하거나 수정한 결과다. 서비스는 저장한 좌표로 공원 경계 경고와 50m 안의 기존 박지(중복 후보)를 함께 돌려준다.
 *
 * @param spotId 제보하거나 수정한 박지의 장소 ID
 * @param parkWarning 공원 경계 경고. 경고이면 근거인 공원 이름, 데이터 출처, 기준일과 고지·안내 문구를 담는다
 * @param guide 제보자에게 보여 줄 안내 문구. 경고가 아닌 박지에도 있다
 * @param duplicateCandidates 가까운 순서의 중복 후보. 없으면 빈 목록
 */
public record BakjiSubmission(
        long spotId, SpotParkWarning parkWarning, String guide, List<BakjiDuplicateCandidate> duplicateCandidates) {

    public BakjiSubmission {
        duplicateCandidates = List.copyOf(duplicateCandidates);
    }
}
