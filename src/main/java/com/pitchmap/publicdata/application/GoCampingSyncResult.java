package com.pitchmap.publicdata.application;

import com.pitchmap.spot.application.PublicSpotUpsertResult;

/**
 * 고캠핑 동기화를 한 번 실행한 결과. 건수는 이번 실행이 처리한 페이지만 센다. 그래서 실패한 실행을 이어받은 실행이면 이어받기 전 페이지는 들어 있지 않다.
 *
 * @param runId 이번 실행의 실행 기록 ID
 * @param pages 이번 실행이 처리한 페이지 수
 * @param processedCount 이번 실행이 받은 항목 수. 건너뛴 항목과 원천에서 삭제된 항목도 센다.
 * @param skipped 좌표나 필수 값이 잘못돼 적재하지 않은 항목 수. 변환할 때 걸러진 항목과 장소 모듈이 거부한 항목을 합친다. 원천에서 삭제된 항목은 세지 않는다.
 * @param removed 원천에서 삭제돼 이번 실행이 숨긴 장소 수. 이미 숨겨진 장소는 세지 않는다.
 */
public record GoCampingSyncResult(
        long runId, int pages, int processedCount, int inserted, int updated, int unchanged, int skipped, int removed) {

    static GoCampingSyncResult empty(long runId) {
        return new GoCampingSyncResult(runId, 0, 0, 0, 0, 0, 0, 0);
    }

    GoCampingSyncResult addPage(int itemCount, int convertSkipped, PublicSpotUpsertResult upserted, int hidden) {
        return new GoCampingSyncResult(
                runId,
                pages + 1,
                processedCount + itemCount,
                inserted + upserted.inserted(),
                updated + upserted.updated(),
                unchanged + upserted.unchanged(),
                skipped + convertSkipped + upserted.skipped(),
                removed + hidden);
    }
}
