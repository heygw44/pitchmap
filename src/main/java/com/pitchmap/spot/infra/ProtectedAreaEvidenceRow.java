package com.pitchmap.spot.infra;

import java.time.LocalDate;

/**
 * 공원 경계 경고의 근거로 보여 줄 경계 한 곳의 이름, 데이터 출처, 기준일. MyBatis가 이름으로 매핑하므로, 구성요소 이름은 열 별칭을 camelCase로
 * 바꾼 것과 같아야 한다.
 */
public record ProtectedAreaEvidenceRow(String name, String source, LocalDate sourceDate) {}
