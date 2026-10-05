package com.pitchmap.spot.application;

/**
 * 공공데이터 장소 한 묶음을 적재한 결과. 네 값을 더하면 호출하는 쪽이 넘긴 명령 수와 같다.
 *
 * <p>skipped에는 값이 잘못돼 저장하지 않은 명령과, 같은 묶음에서 외부 ID가 겹쳐 뒤의 명령에 밀린 명령이 들어간다.
 */
public record PublicSpotUpsertResult(int inserted, int updated, int unchanged, int skipped) {}
