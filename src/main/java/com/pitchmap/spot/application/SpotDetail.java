package com.pitchmap.spot.application;

import com.pitchmap.spot.domain.SpotType;
import java.util.List;

/**
 * 장소 상세 조회 결과. lat, lng는 위도와 경도이고, parkWarning은 장소가 공원 경계 안일 가능성이 있는지와 그 근거를 뜻한다.
 *
 * <p>서비스는 bakji와 publicDetail 중 장소 유형에 맞는 하나만 채운다. 박지면 bakji를, 야영장과 자연휴양림이면 publicDetail을 채우고 다른 하나는
 * {@code null}로 둔다.
 *
 * <p>rating은 후기가 없어도 채우고, recentReviews는 후기가 없으면 빈 목록이다.
 *
 * <p>weather는 기상청 호출이 실패하면 {@code null}이다.
 */
public record SpotDetail(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        String address,
        SpotParkWarning parkWarning,
        SpotBakjiDetail bakji,
        SpotPublicDetail publicDetail,
        SpotRating rating,
        List<SpotRecentReview> recentReviews,
        SpotWeather weather) {

    SpotDetail withWeather(SpotWeather weather) {
        return new SpotDetail(
                spotId,
                type,
                name,
                lat,
                lng,
                address,
                parkWarning,
                bakji,
                publicDetail,
                rating,
                recentReviews,
                weather);
    }
}
