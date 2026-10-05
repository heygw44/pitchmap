package com.pitchmap.spot.api;

import com.pitchmap.spot.application.PublicSpotOperatingStatus;
import com.pitchmap.spot.application.PublicSpotSource;
import com.pitchmap.spot.application.SpotBakjiDetail;
import com.pitchmap.spot.application.SpotDetail;
import com.pitchmap.spot.application.SpotFacilities;
import com.pitchmap.spot.application.SpotPublicDetail;
import com.pitchmap.spot.domain.SpotType;
import java.time.LocalDate;
import java.util.List;

// 날씨, 평점, 최근 후기, 예상 인원, 모집 중인 베이스캠프는 아직 채우지 않는다.
// 클라이언트가 응답 모양에 맞춰 화면을 만들 수 있도록 서버는 빈 값(null, 0, 빈 배열)을 넣어 준다.
public record SpotDetailResponse(
        long spotId,
        SpotType type,
        String name,
        double lat,
        double lng,
        String address,
        Bakji bakji,
        PublicDetail publicDetail,
        ParkWarning parkWarning,
        Rating rating,
        List<Object> recentReviews,
        List<Object> expectedPeople,
        List<Object> recruitingBasecamps,
        Object weather) {

    static SpotDetailResponse from(SpotDetail detail) {
        return new SpotDetailResponse(
                detail.spotId(),
                detail.type(),
                detail.name(),
                detail.lat(),
                detail.lng(),
                detail.address(),
                detail.bakji() == null ? null : Bakji.from(detail.bakji()),
                detail.publicDetail() == null ? null : PublicDetail.from(detail.publicDetail()),
                new ParkWarning(detail.parkWarning()),
                new Rating(null, 0),
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    public record Bakji(
            String description,
            boolean hasWater,
            boolean hasToilet,
            String signalLevel,
            long confirmationCount,
            Reporter reporter) {

        static Bakji from(SpotBakjiDetail bakji) {
            return new Bakji(
                    bakji.description(),
                    bakji.hasWater(),
                    bakji.hasToilet(),
                    bakji.signalLevel(),
                    bakji.confirmationCount(),
                    new Reporter(bakji.reporterId(), bakji.reporterNickname()));
        }
    }

    public record Reporter(long memberId, String nickname) {}

    public record PublicDetail(
            PublicSpotSource source,
            String category,
            Facilities facilities,
            String phone,
            String homepage,
            LocalDate sourceDate,
            PublicSpotOperatingStatus operatingStatus,
            LocalDate closedFrom,
            LocalDate closedUntil,
            boolean closedNow) {

        static PublicDetail from(SpotPublicDetail detail) {
            return new PublicDetail(
                    detail.source(),
                    detail.category(),
                    detail.facilities() == null ? null : Facilities.from(detail.facilities()),
                    detail.phone(),
                    detail.homepage(),
                    detail.sourceDate(),
                    detail.operatingStatus(),
                    detail.closedFrom(),
                    detail.closedUntil(),
                    detail.closedNow());
        }
    }

    public record Facilities(
            String toiletCount,
            String showerCount,
            String sinkCount,
            String brazier,
            String amenities,
            String amenitiesEtc,
            String nearbyFacilities,
            String nearbyFacilitiesEtc,
            String petPolicy) {

        static Facilities from(SpotFacilities facilities) {
            return new Facilities(
                    facilities.toiletCount(),
                    facilities.showerCount(),
                    facilities.sinkCount(),
                    facilities.brazier(),
                    facilities.amenities(),
                    facilities.amenitiesEtc(),
                    facilities.nearbyFacilities(),
                    facilities.nearbyFacilitiesEtc(),
                    facilities.petPolicy());
        }
    }

    public record ParkWarning(boolean warned) {}

    public record Rating(Double average, long count) {}
}
