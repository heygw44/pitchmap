package com.pitchmap.spot.domain;

/**
 * 제보자가 박지에 적는 내용이다. 설명, 통신 상태, 바닥 유형은 모르면 비워 두고, 물과 화장실 유무는 꼭 적는다.
 *
 * @param description 설명. 없으면 null
 * @param signalLevel 통신 상태. 모르면 null
 * @param groundType 바닥 유형. 모르면 null
 */
public record BakjiContent(
        String description,
        boolean hasWater,
        boolean hasToilet,
        BakjiSignalLevel signalLevel,
        BakjiGroundType groundType) {

    /** 설명의 최대 길이다. DB 컬럼 크기와 같다. */
    public static final int DESCRIPTION_MAX_LENGTH = 2000;

    public BakjiContent {
        requireValidDescription(description);
    }

    /** 호출하면 설명이 {@value #DESCRIPTION_MAX_LENGTH}자를 넘을 때 {@link IllegalArgumentException}을 던진다. null은 통과한다. */
    public static void requireValidDescription(String description) {
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("설명은 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
