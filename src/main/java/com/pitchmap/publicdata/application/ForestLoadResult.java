package com.pitchmap.publicdata.application;

import com.pitchmap.spot.application.PublicSpotUpsertResult;
import java.time.LocalDate;

/**
 * 휴양림 파일을 한 번 적재한 결과. 건수는 행 수가 아니라 기관아이디로 중복을 없앤 휴양림 수로 센다.
 *
 * @param sourceDate 적재한 파일의 기준일
 * @param processedCount 파일에 있는 휴양림 수. 건너뛴 휴양림도 센다.
 * @param skipped 적재하지 않은 휴양림 수. 위경도를 숫자로 읽지 못한 휴양림과 장소 모듈이 거부한 휴양림을 합친다.
 */
public record ForestLoadResult(
        LocalDate sourceDate, int processedCount, int inserted, int updated, int unchanged, int skipped) {

    static ForestLoadResult of(
            LocalDate sourceDate, int processedCount, int parseSkipped, PublicSpotUpsertResult upserted) {
        return new ForestLoadResult(
                sourceDate,
                processedCount,
                upserted.inserted(),
                upserted.updated(),
                upserted.unchanged(),
                parseSkipped + upserted.skipped());
    }
}
