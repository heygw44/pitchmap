package com.pitchmap.spot.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pitchmap.spot.application.PublicSpotOperatingStatus;
import com.pitchmap.spot.application.PublicSpotSource;
import com.pitchmap.spot.application.SpotBakjiDetail;
import com.pitchmap.spot.application.SpotDetail;
import com.pitchmap.spot.application.SpotExpectedPeople;
import com.pitchmap.spot.application.SpotFacilities;
import com.pitchmap.spot.application.SpotJoinCondition;
import com.pitchmap.spot.application.SpotPublicDetail;
import com.pitchmap.spot.application.SpotRating;
import com.pitchmap.spot.application.SpotRecentReview;
import com.pitchmap.spot.application.SpotRecruitingBasecamp;
import com.pitchmap.spot.application.SpotWeather;
import com.pitchmap.spot.domain.SpotType;
import com.pitchmap.weather.application.SunTimes;
import com.pitchmap.weather.application.WeatherForecast;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// expectedPeople은 오늘부터 야영하는 밤별 인원이고, 인원이 있는 날짜만 날짜 순으로 준다. recruitingBasecamps는 모집 중인 베이스캠프만 출발일 순으로 준다.
// 둘 다 없으면 빈 배열이다.
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
        ParkWarningResponse parkWarning,
        Rating rating,
        List<RecentReview> recentReviews,
        List<ExpectedPeople> expectedPeople,
        List<RecruitingBasecamp> recruitingBasecamps,
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
                ParkWarningResponse.from(detail.parkWarning()),
                Rating.from(detail.rating()),
                detail.recentReviews().stream().map(RecentReview::from).toList(),
                detail.expectedPeople().stream().map(ExpectedPeople::from).toList(),
                detail.recruitingBasecamps().stream()
                        .map(RecruitingBasecamp::from)
                        .toList(),
                detail.weather() == null ? null : Weather.from(detail.weather()));
    }

    public record ExpectedPeople(LocalDate date, int count) {

        static ExpectedPeople from(SpotExpectedPeople expectedPeople) {
            return new ExpectedPeople(expectedPeople.date(), expectedPeople.count());
        }
    }

    // 베이스캠프 검색 결과의 항목에서 장소와 상태, 신청 가능 여부를 뺀 모양이다. 신청 가능 여부는 로그인한 회원마다 달라서 베이스캠프 상세에서 본다.
    public record RecruitingBasecamp(
            long basecampId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            JoinCondition joinCondition) {

        static RecruitingBasecamp from(SpotRecruitingBasecamp basecamp) {
            return new RecruitingBasecamp(
                    basecamp.basecampId(),
                    basecamp.title(),
                    basecamp.startDate(),
                    basecamp.endDate(),
                    basecamp.capacity(),
                    basecamp.headcount(),
                    JoinCondition.from(basecamp.joinCondition()));
        }
    }

    public record JoinCondition(
            Integer minTrustLevel, Integer ageGroupMin, Integer ageGroupMax, boolean sameGenderOnly) {

        static JoinCondition from(SpotJoinCondition condition) {
            return new JoinCondition(
                    condition.minTrustLevel(),
                    condition.ageGroupMin(),
                    condition.ageGroupMax(),
                    condition.sameGenderOnly());
        }
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
