package com.pitchmap.publicdata.infra;

import java.time.LocalDate;
import java.util.List;

/**
 * 국립자연휴양림 예약 정책 파일 하나를 읽은 결과.
 *
 * @param sourceDate 파일 기준일. 파일 이름 끝의 날짜에서 읽는다.
 * @param records 파일의 데이터 행을 파일 순서대로 담는다. 예약 정책마다 한 행이라 같은 휴양림이 여러 번 나온다.
 */
public record ForestFile(LocalDate sourceDate, List<ForestRecord> records) {

    public ForestFile {
        records = List.copyOf(records);
    }
}
