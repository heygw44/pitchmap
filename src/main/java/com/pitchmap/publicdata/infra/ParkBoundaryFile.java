package com.pitchmap.publicdata.infra;

import java.time.LocalDate;
import java.util.List;

/**
 * 공원 경계 파일 하나를 읽은 결과.
 *
 * @param sourceDate 파일 기준일. 파일 이름 끝의 날짜에서 읽는다.
 * @param records 파일의 데이터 행을 파일 순서대로 담는다. 공원마다 한 행이고, 같은 (구분, 이름)은 두 번 나오지 않는다.
 */
public record ParkBoundaryFile(LocalDate sourceDate, List<ParkBoundaryRecord> records) {

    public ParkBoundaryFile {
        records = List.copyOf(records);
    }
}
