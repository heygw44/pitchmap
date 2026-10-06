package com.pitchmap.publicdata.application;

import com.pitchmap.spot.application.ProtectedAreaUpsertResult;
import java.time.LocalDate;

/**
 * 공원 경계 파일을 한 번 적재한 결과. 리더가 형식이 깨진 파일을 통째로 거부하므로 건너뛴 공원 수는 없다.
 *
 * @param sourceDate 적재한 파일의 기준일
 * @param processedCount 파일에 있는 공원 수. inserted와 updated를 더한 값과 같다.
 * @param inserted 새로 추가한 공원 수
 * @param updated 이미 있어서 경계와 기준일을 바꾼 공원 수
 */
public record ParkBoundaryLoadResult(LocalDate sourceDate, int processedCount, int inserted, int updated) {

    static ParkBoundaryLoadResult of(LocalDate sourceDate, int processedCount, ProtectedAreaUpsertResult upserted) {
        return new ParkBoundaryLoadResult(sourceDate, processedCount, upserted.inserted(), upserted.updated());
    }
}
