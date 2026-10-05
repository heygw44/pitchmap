package com.pitchmap.spot.application;

import com.pitchmap.spot.domain.SpotClosure;
import com.pitchmap.spot.domain.SpotType;
import java.time.LocalDate;

/** 지도 영역 조회와 반경 검색이 같은 규칙으로 장소의 휴장 여부를 계산하도록 모아 둔 도우미. */
final class SpotClosedNow {

    private SpotClosedNow() {}

    /**
     * 호출하면 today(한국 날짜)에 장소가 휴장인지 돌려준다. operatingStatus는 운영 상태 이름이고, 공공데이터 상세가 없으면 {@code null}이다.
     */
    static boolean isClosedOn(
            SpotType type, String operatingStatus, LocalDate closedFrom, LocalDate closedUntil, LocalDate today) {
        // 박지는 사용자가 제보한 장소라 운영 상태나 휴장 기간이 없다. 그래서 서버는 박지를 항상 휴장이 아닌 것으로 본다.
        if (type == SpotType.BAKJI) {
            return false;
        }
        SpotClosure closure = new SpotClosure(toOperatingStatus(operatingStatus), closedFrom, closedUntil);
        return closure.isClosedOn(today);
    }

    private static PublicSpotOperatingStatus toOperatingStatus(String operatingStatus) {
        if (operatingStatus == null) {
            return null;
        }
        return PublicSpotOperatingStatus.valueOf(operatingStatus);
    }
}
