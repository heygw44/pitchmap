package com.pitchmap.weather.domain;

import java.util.Comparator;
import java.util.List;

/**
 * 중기예보 구역 목록. 장소 좌표에서 가장 가까운 구역을 고른다.
 *
 * <p>기상청 중기예보는 구역 단위로만 주고 좌표로 조회하는 방법이 없다. 그래서 서버가 구역마다 대표 지점을 두고, 장소에서 가장 가까운 지점의
 * 구역으로 조회한다.
 */
public class MidTermRegions {

    private final List<MidTermRegion> regions;

    public MidTermRegions(List<MidTermRegion> regions) {
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("중기예보 구역 목록이 비어 있다");
        }
        this.regions = List.copyOf(regions);
    }

    /** 좌표에서 가장 가까운 구역을 돌려준다. */
    public MidTermRegion nearest(double lat, double lng) {
        return regions.stream()
                .min(Comparator.comparingDouble(region -> squaredDistance(region, lat, lng)))
                .orElseThrow();
    }

    // 한반도 안에서 가까운 구역을 고르는 데는 평면 근사로 충분하다. 경도 1도의 길이는 위도에 따라 줄어서 cos(위도)를 곱한다.
    private static double squaredDistance(MidTermRegion region, double lat, double lng) {
        double dLat = region.lat() - lat;
        double dLng = (region.lng() - lng) * Math.cos(Math.toRadians(lat));
        return dLat * dLat + dLng * dLng;
    }
}
