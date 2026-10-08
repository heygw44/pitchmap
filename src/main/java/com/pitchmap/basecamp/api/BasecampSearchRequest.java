package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampSearchQuery;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.springframework.format.annotation.DateTimeFormat;

// 화면이 쿼리 파라미터로 보내는 베이스캠프 검색 조건이다. 지역은 영역(swLat, swLng, neLat, neLng) 네 개나 반경(lat, lng, radiusKm) 세 개 중
// 한 묶음만 모두 보내야 한다. 두 묶음 모두 값이 비어 있을 수 있어서 개별 값에는 @NotNull을 걸지 않고, 묶음 단위로 @AssertTrue가 검사한다.
// 반경의 상한(50km)은 장소 검색과 달리 전용 오류 코드 없이 입력 형식 오류(INVALID_INPUT)로 알린다.
public record BasecampSearchRequest(
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.")
        Double swLat,

        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.")
        Double swLng,

        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.")
        Double neLat,

        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.")
        Double neLng,

        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.")
        Double lat,

        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.")
        Double lng,

        @DecimalMin(value = "0", inclusive = false, message = "반경은 0보다 커야 합니다.")
        @DecimalMax(value = "50", message = "반경은 50km 이하여야 합니다.")
        Double radiusKm,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        Boolean hasVacancy,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    @AssertTrue(message = "지역은 swLat, swLng, neLat, neLng 네 개나 lat, lng, radiusKm 세 개 중 한 묶음만 모두 보내야 합니다.")
    public boolean isRegionSpecified() {
        return (hasFullArea() && hasNoRadius()) || (hasNoArea() && hasFullRadius());
    }

    // 위도나 경도가 빠졌으면 지역 묶음 검사가 따로 오류를 내므로, 여기서는 네 값이 모두 있을 때만 순서를 본다.
    @AssertTrue(message = "남서쪽 위도는 북동쪽 위도보다 작아야 합니다.")
    public boolean isLatitudeOrdered() {
        return !hasFullArea() || swLat < neLat;
    }

    @AssertTrue(message = "남서쪽 경도는 북동쪽 경도보다 작아야 합니다.")
    public boolean isLongitudeOrdered() {
        return !hasFullArea() || swLng < neLng;
    }

    @AssertTrue(message = "출발일 범위의 시작(fromDate)은 끝(toDate)보다 늦을 수 없습니다.")
    public boolean isDateRangeOrdered() {
        return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
    }

    BasecampSearchQuery toQuery() {
        BasecampSearchQuery.Region region = hasFullArea()
                ? new BasecampSearchQuery.Area(swLat, swLng, neLat, neLng)
                : new BasecampSearchQuery.Radius(lat, lng, radiusKm);
        return new BasecampSearchQuery(
                region,
                fromDate,
                toDate,
                Boolean.TRUE.equals(hasVacancy),
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size);
    }

    private boolean hasFullArea() {
        return Stream.of(swLat, swLng, neLat, neLng).allMatch(value -> value != null);
    }

    private boolean hasNoArea() {
        return Stream.of(swLat, swLng, neLat, neLng).allMatch(value -> value == null);
    }

    private boolean hasFullRadius() {
        return Stream.of(lat, lng, radiusKm).allMatch(value -> value != null);
    }

    private boolean hasNoRadius() {
        return Stream.of(lat, lng, radiusKm).allMatch(value -> value == null);
    }
}
