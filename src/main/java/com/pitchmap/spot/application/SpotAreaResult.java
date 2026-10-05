package com.pitchmap.spot.application;

import java.util.List;

/** 지도 영역 조회 결과. 서버는 markers와 clusters 중 하나만 채우고, 나머지는 빈 목록으로 둔다. */
public record SpotAreaResult(List<SpotMarker> markers, List<SpotCluster> clusters) {

    public SpotAreaResult {
        markers = List.copyOf(markers);
        clusters = List.copyOf(clusters);
    }
}
