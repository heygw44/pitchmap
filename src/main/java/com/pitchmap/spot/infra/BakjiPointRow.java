package com.pitchmap.spot.infra;

/** 박지 재판정이 읽은 박지 한 건의 ID와 좌표. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로 바꾼 것과 같아야 한다. */
public record BakjiPointRow(long id, double lat, double lng) {}
