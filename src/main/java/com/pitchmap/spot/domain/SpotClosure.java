package com.pitchmap.spot.domain;

import com.pitchmap.spot.application.PublicSpotOperatingStatus;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 공공 장소의 운영 상태와 휴장 기간. 세 값 모두 비어 있을 수 있다.
 *
 * <p>원본 데이터에는 휴장 기간이 끝났는데도 휴장으로 남아 있는 곳이 많고, 운영 중으로 표시됐는데 휴장 기간이 있는 곳도 있다. 그래서 서버는
 * 운영 상태만 보고 휴장인지 판단하지 않고, 휴장 기간이 하나라도 있으면 기간을 먼저 본다.
 *
 * <p>휴장인 장소를 지도에서 숨길지는 호출하는 쪽이 정한다. 지금 지도 조회는 숨기지 않고 휴장 표시만 붙인다.
 */
public record SpotClosure(PublicSpotOperatingStatus operatingStatus, LocalDate closedFrom, LocalDate closedUntil) {

    /**
     * 주어진 날짜에 휴장인지 판단한다.
     *
     * <p>휴장 시작일이나 종료일이 하나라도 있으면 그 기간 안(양 끝 포함)일 때만 휴장이다. 비어 있는 쪽 끝은 제한이 없다고 본다. 이때 운영
     * 상태는 보지 않는다. 기간이 둘 다 없으면 운영 상태가 임시 휴장이나 폐업일 때 휴장이다.
     *
     * @param today 판단할 날짜(한국 날짜). null이면 NullPointerException을 던진다
     */
    public boolean isClosedOn(LocalDate today) {
        Objects.requireNonNull(today, "판단할 날짜가 없습니다.");
        if (closedFrom != null || closedUntil != null) {
            boolean started = closedFrom == null || !today.isBefore(closedFrom);
            boolean notEnded = closedUntil == null || !today.isAfter(closedUntil);
            return started && notEnded;
        }
        return operatingStatus == PublicSpotOperatingStatus.TEMPORARILY_CLOSED
                || operatingStatus == PublicSpotOperatingStatus.PERMANENTLY_CLOSED;
    }
}
