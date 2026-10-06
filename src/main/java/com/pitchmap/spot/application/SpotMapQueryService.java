package com.pitchmap.spot.application;

import com.pitchmap.spot.domain.ClusterGridShape;
import com.pitchmap.spot.infra.SpotAreaCondition;
import com.pitchmap.spot.infra.SpotAreaMapper;
import com.pitchmap.spot.infra.SpotClusterGrid;
import com.pitchmap.spot.infra.SpotClusterRow;
import com.pitchmap.spot.infra.SpotMarkerRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 화면 영역 안의 ACTIVE 장소를 찾는다.
 *
 * <p>한 응답에는 마커와 묶음을 합쳐 {@value #MAX_ITEMS}개까지만 담는다. 그래서 서버는 영역 안 장소가 {@value #MAX_ITEMS}개 이하이면 모두 마커로
 * 주고, 넘으면 마커를 비우고 영역을 칸으로 나눈 칸별 묶음으로 준다. 열 수와 행 수는 화면 크기(px)로 정하고({@link ClusterGridShape}), 전체 칸은
 * 많아야 {@value ClusterGridShape#MAX_CELLS}개라서 묶음도 상한을 넘지 않는다.
 *
 * <p>공공데이터 장소의 휴장 여부는 조회한 날의 한국 날짜로 계산한다. 서버는 휴장 중인 장소도 숨기지 않고 표시만 한다.
 */
@Service
@RequiredArgsConstructor
public class SpotMapQueryService {

    static final int MAX_ITEMS = 500;

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final SpotAreaMapper spotAreaMapper;
    private final Clock clock;

    /**
     * 호출하면 query 조건에 맞는 영역 안 장소를 마커나 칸별 묶음으로 돌려준다. 마커는 장소 ID 순서이고, 묶음은 남쪽 행부터, 같은 행에서는 서쪽 칸부터
     * 나온다.
     *
     * <p>마커 조회와 묶음 조회를 읽기 전용 트랜잭션 하나에서 실행한다. 그래서 MySQL은 두 조회에 같은 스냅숏을 보여 주고, 그 사이 다른 트랜잭션이 장소를
     * 바꿔도 마커 수로 내린 판단과 묶음 결과가 어긋나지 않는다.
     */
    @Transactional(readOnly = true)
    public SpotAreaResult findInArea(SpotAreaQuery query) {
        SpotAreaCondition condition = toCondition(query);
        // 상한보다 한 건 더 읽으면, 개수를 따로 세지 않고도 상한을 넘는지 알 수 있다.
        List<SpotMarkerRow> rows = spotAreaMapper.selectMarkers(condition, MAX_ITEMS + 1);
        if (rows.size() > MAX_ITEMS) {
            return new SpotAreaResult(List.of(), findClusters(query, condition));
        }
        LocalDate today = LocalDate.now(clock.withZone(KOREA));
        List<SpotMarker> markers =
                rows.stream().map(row -> toMarker(row, today)).toList();
        return new SpotAreaResult(markers, List.of());
    }

    private List<SpotCluster> findClusters(SpotAreaQuery query, SpotAreaCondition condition) {
        return spotAreaMapper.selectClusters(condition, toGrid(query)).stream()
                .map(SpotMapQueryService::toCluster)
                .toList();
    }

    private static SpotClusterGrid toGrid(SpotAreaQuery query) {
        ClusterGridShape shape = ClusterGridShape.forScreen(query.screenWidth(), query.screenHeight());
        return new SpotClusterGrid(
                (query.neLat() - query.swLat()) / shape.rows(),
                (query.neLng() - query.swLng()) / shape.columns(),
                shape.rows() - 1,
                shape.columns() - 1);
    }

    private static SpotAreaCondition toCondition(SpotAreaQuery query) {
        return new SpotAreaCondition(
                query.swLat(),
                query.swLng(),
                query.neLat(),
                query.neLng(),
                query.types(),
                query.hasWater(),
                query.hasToilet(),
                query.excludeWarning());
    }

    private static SpotMarker toMarker(SpotMarkerRow row, LocalDate today) {
        return new SpotMarker(
                row.spotId(), row.type(), row.name(), row.lat(), row.lng(), row.parkWarning(), isClosedOn(row, today));
    }

    private static boolean isClosedOn(SpotMarkerRow row, LocalDate today) {
        return SpotClosedNow.isClosedOn(row.type(), row.operatingStatus(), row.closedFrom(), row.closedUntil(), today);
    }

    private static SpotCluster toCluster(SpotClusterRow row) {
        return new SpotCluster(row.lat(), row.lng(), row.spotCount());
    }
}
