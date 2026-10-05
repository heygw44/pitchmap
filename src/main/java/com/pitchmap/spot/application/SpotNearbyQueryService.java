package com.pitchmap.spot.application;

import com.pitchmap.spot.infra.SpotNearbyCondition;
import com.pitchmap.spot.infra.SpotNearbyMapper;
import com.pitchmap.spot.infra.SpotNearbyRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 중심 좌표에서 반경 안에 있는 ACTIVE 장소를 가까운 순서로 찾는다.
 *
 * <p>공공데이터 장소의 휴장 여부는 조회한 날의 한국 날짜로 계산한다. 서버는 휴장 중인 장소도 빼지 않고 표시만 한다.
 */
@Service
@RequiredArgsConstructor
public class SpotNearbyQueryService {

    // 위도 1도의 거리를 짧게 잡은 값이다. 실제 거리는 위도에 따라 110.6~111.7km라서, 이 값으로 나누면 후보 사각형이 조금 넓어진다.
    private static final double KM_PER_DEGREE = 111.0;

    // 후보 사각형은 공간 인덱스로 후보를 좁히는 데만 쓰고, 반경 안인지는 매퍼의 구면 거리 조건이 정한다.
    // 그래서 사각형이 반경보다 좁으면 경계 근처 장소가 빠지지만, 넓으면 거리 조건이 걸러 내므로 결과가 바뀌지 않는다.
    // 위도 1도 거리의 차이와 경도 계산의 근삿값을 덮으려고 사각형을 10% 넓힌다.
    private static final double BOX_MARGIN = 1.1;

    // 이보다 cos(위도)가 작으면 극 근처라서 경도 폭을 계산하지 않고 경도 전체를 후보로 본다.
    private static final double MIN_COS_LATITUDE = 1e-6;

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final SpotNearbyMapper spotNearbyMapper;
    private final Clock clock;

    /**
     * 호출하면 query 조건에 맞는 반경 안 장소를 가까운 순서로 한 페이지 돌려준다. 거리가 같은 장소는 장소 ID 순서로 나온다.
     *
     * <p>서비스는 다음 페이지가 있는지 알려고 한 행을 더 읽고, 그 행은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public SpotNearbyPage findNearby(SpotNearbyQuery query) {
        long offset = (long) query.page() * query.size();
        List<SpotNearbyRow> rows = spotNearbyMapper.selectNearby(toCondition(query), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        LocalDate today = LocalDate.now(clock.withZone(KOREA));
        List<SpotNearbyItem> content =
                rows.stream().limit(query.size()).map(row -> toItem(row, today)).toList();
        return new SpotNearbyPage(content, query.page(), query.size(), hasNext);
    }

    private static SpotNearbyCondition toCondition(SpotNearbyQuery query) {
        double latDelta = query.radiusKm() / KM_PER_DEGREE * BOX_MARGIN;
        double cosLat = Math.cos(Math.toRadians(query.lat()));
        double swLng = -180;
        double neLng = 180;
        if (cosLat > MIN_COS_LATITUDE) {
            double lngDelta = query.radiusKm() / (KM_PER_DEGREE * cosLat) * BOX_MARGIN;
            swLng = Math.max(query.lng() - lngDelta, -180);
            neLng = Math.min(query.lng() + lngDelta, 180);
        }
        return new SpotNearbyCondition(
                query.lat(),
                query.lng(),
                query.radiusKm() * 1000,
                Math.max(query.lat() - latDelta, -90),
                swLng,
                Math.min(query.lat() + latDelta, 90),
                neLng,
                query.types(),
                query.hasWater(),
                query.hasToilet(),
                query.excludeWarning());
    }

    private static SpotNearbyItem toItem(SpotNearbyRow row, LocalDate today) {
        boolean closedNow =
                SpotClosedNow.isClosedOn(row.type(), row.operatingStatus(), row.closedFrom(), row.closedUntil(), today);
        return new SpotNearbyItem(
                row.spotId(),
                row.type(),
                row.name(),
                row.lat(),
                row.lng(),
                row.parkWarning(),
                closedNow,
                toKilometers(row.distanceMeters()));
    }

    // 미터를 km로 바꾸면서 소수 둘째 자리에서 반올림한다. 예를 들어 12,345m는 12.35km가 된다.
    private static double toKilometers(double meters) {
        return Math.round(meters / 10.0) / 100.0;
    }
}
