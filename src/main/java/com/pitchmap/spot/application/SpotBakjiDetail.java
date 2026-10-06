package com.pitchmap.spot.application;

/**
 * 박지 상세. signalLevel은 제보자가 남긴 통신 상태이고, 남기지 않았으면 {@code null}이다.
 *
 * <p>confirmationCount는 회원들이 다녀와서 정보가 맞다고 남긴 확인 수다. reporterId와 reporterNickname은 박지를 제보한 회원의 ID와 지금 닉네임이다.
 */
public record SpotBakjiDetail(
        String description,
        boolean hasWater,
        boolean hasToilet,
        String signalLevel,
        long confirmationCount,
        long reporterId,
        String reporterNickname) {}
