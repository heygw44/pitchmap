package com.pitchmap.spot.infra;

/** 중복 후보 조회가 읽은 박지 한 건. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다. distanceMeters는 중심에서 박지까지의 구면 거리(m)다. */
public record BakjiDuplicateRow(long spotId, String name, double distanceMeters) {}
