package com.pitchmap.spot.application;

/**
 * 공원·보호지역 경계 한 묶음을 적재한 결과. 두 값을 더하면 호출하는 쪽이 넘긴 명령 수와 같다.
 *
 * @param inserted 새로 추가한 경계 수
 * @param updated 같은 출처·구분·이름의 경계가 이미 있어서 경계와 기준일을 바꾼 경계 수. 값이 그대로여도 다시 썼으면 센다.
 */
public record ProtectedAreaUpsertResult(int inserted, int updated) {}
