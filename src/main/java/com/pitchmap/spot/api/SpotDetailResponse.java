package com.pitchmap.spot.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.pitchmap.spot.application.PublicSpotOperatingStatus;
import com.pitchmap.spot.application.PublicSpotSource;
import com.pitchmap.spot.application.SpotBakjiDetail;
import com.pitchmap.spot.application.SpotDetail;
import com.pitchmap.spot.application.SpotFacilities;
import com.pitchmap.spot.application.SpotParkWarning;
import com.pitchmap.spot.application.SpotPublicDetail;
import com.pitchmap.spot.application.SpotRating;
import com.pitchmap.spot.application.SpotRecentReview;
import com.pitchmap.spot.application.SpotWeather;
import com.pitchmap.spot.domain.SpotType;
import com.pitchmap.weather.application.SunTimes;
import com.pitchmap.weather.application.WeatherForecast;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// 예상 인원과 모집 중인 베이스캠프는 아직 채우지 않는다.
// 클라이언트가 응답 모양에 맞춰 화면을 만들 수 있도록 서버는 빈 값(null, 0, 빈 배열)을 넣어 준다.
// parkWarning은 경고가 아니면 warned만 내보낸다. weather는 기상청 호출이 실패하면 null이고, 천문연만 실패하면 weather 안의 sun이 null이다.
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
        List<RecentReview> recentReviews,
        List<Object> expectedPeople,
        List<Object> recruitingBasecamps,
        Weather weather) {

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
                ParkWarning.from(detail.parkWarning()),
                Rating.from(detail.rating()),
                detail.recentReviews().stream().map(RecentReview::from).toList(),
                List.of(),
                List.of(),
                detail.weather() == null ? null : Weather.from(detail.weather()));
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

    /** 경고가 아니면 warned만 내보낸다. 경고인데 공원 경계 행이 없으면 areaName, source, sourceDate도 뺀다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParkWarning(
            boolean warned, String areaName, String source, LocalDate sourceDate, String notice, String guide) {

        static ParkWarning from(SpotParkWarning warning) {
            return new ParkWarning(
                    warning.warned(),
                    warning.areaName(),
                    warning.source(),
                    warning.sourceDate(),
                    warning.notice(),
                    warning.guide());
        }
    }

    /** sun은 천문연이 실패해도 필드를 남기고 null로 준다. */
    public record Weather(String source, List<ShortTerm> shortTerm, List<MidTerm> midTerm, Sun sun) {

        static Weather from(SpotWeather weather) {
            WeatherForecast forecast = weather.forecast();
            return new Weather(
                    forecast.source(),
                    forecast.shortTerm().stream().map(ShortTerm::from).toList(),
                    forecast.midTerm().stream().map(MidTerm::from).toList(),
                    weather.sun() == null ? null : Sun.from(weather.sun()));
        }
    }

    public record ShortTerm(Instant at, Integer temperature, Integer precipitationProbability, Double windSpeed) {

        static ShortTerm from(WeatherForecast.ShortTermForecast forecast) {
            return new ShortTerm(
                    forecast.at(), forecast.temperature(), forecast.precipitationProbability(), forecast.windSpeed());
        }
    }

    public record MidTerm(
            LocalDate date,
            Integer minTemperature,
            Integer maxTemperature,
            String amSky,
            String pmSky,
            Integer amPrecipitationProbability,
            Integer pmPrecipitationProbability) {

        static MidTerm from(WeatherForecast.MidTermForecast forecast) {
            return new MidTerm(
                    forecast.date(),
                    forecast.minTemperature(),
                    forecast.maxTemperature(),
                    forecast.amSky(),
                    forecast.pmSky(),
                    forecast.amPrecipitationProbability(),
                    forecast.pmPrecipitationProbability());
        }
    }

    // 시각은 초 없이 "HH:mm"(한국 시각)으로 내보낸다.
    public record Sun(
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime sunrise,
            @JsonFormat(pattern = "HH:mm") LocalTime sunset,
            @JsonFormat(pattern = "HH:mm") LocalTime civilTwilightEnd) {

        static Sun from(SunTimes sun) {
            return new Sun(sun.date(), sun.sunrise(), sun.sunset(), sun.civilTwilightEnd());
        }
    }

    // 평균은 후기가 없으면 null이다. 후기 수는 항상 준다.
    public record Rating(Double average, long count) {

        static Rating from(SpotRating rating) {
            return new Rating(rating.average(), rating.count());
        }
    }

    public record Author(long memberId, String nickname) {}

    // 후기 목록의 항목과 같은 모양이다. 작성자는 회원 ID와 닉네임만 내보낸다.
    public record RecentReview(
            long reviewId, Author author, LocalDate visitedDate, int rating, String content, Instant createdAt) {

        static RecentReview from(SpotRecentReview review) {
            return new RecentReview(
                    review.reviewId(),
                    new Author(review.authorId(), review.authorNickname()),
                    review.visitedDate(),
                    review.rating(),
                    review.content(),
                    review.createdAt());
        }
    }
}
