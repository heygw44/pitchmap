package com.pitchmap.spot.application;

/**
 * 중심 좌표 둘레의 반경 원을 모두 덮는 후보 사각형이다. 매퍼는 이 사각형으로 공간 인덱스에서 후보를 좁히고, 반경 안인지는 구면 거리 조건으로 따로
 * 가린다.
 */
public record SpotSearchBox(double swLat, double swLng, double neLat, double neLng) {

    // 위도 1도의 거리를 짧게 잡은 값이다. 실제 거리는 위도에 따라 110.6~111.7km라서, 이 값으로 나누면 후보 사각형이 조금 넓어진다.
    private static final double KM_PER_DEGREE = 111.0;

    // 후보 사각형은 공간 인덱스로 후보를 좁히는 데만 쓰고, 반경 안인지는 매퍼의 구면 거리 조건이 정한다.
    // 그래서 사각형이 반경보다 좁으면 경계 근처 장소가 빠지지만, 넓으면 거리 조건이 걸러 내므로 결과가 바뀌지 않는다.
    // 위도 1도 거리의 차이와 경도 계산의 근삿값을 덮으려고 사각형을 10% 넓힌다.
    private static final double BOX_MARGIN = 1.1;

    // 이보다 cos(위도)가 작으면 극 근처라서 경도 폭을 계산하지 않고 경도 전체를 후보로 본다.
    private static final double MIN_COS_LATITUDE = 1e-6;

    /** 호출하면 (lat, lng)을 중심으로 반경 radiusKm 원을 덮는 사각형을 돌려준다. */
    public static SpotSearchBox around(double lat, double lng, double radiusKm) {
        double latDelta = radiusKm / KM_PER_DEGREE * BOX_MARGIN;
        double cosLat = Math.cos(Math.toRadians(lat));
        double swLng = -180;
        double neLng = 180;
        if (cosLat > MIN_COS_LATITUDE) {
            double lngDelta = radiusKm / (KM_PER_DEGREE * cosLat) * BOX_MARGIN;
            swLng = Math.max(lng - lngDelta, -180);
            neLng = Math.min(lng + lngDelta, 180);
        }
        return new SpotSearchBox(Math.max(lat - latDelta, -90), swLng, Math.min(lat + latDelta, 90), neLng);
    }
}
