package com.pitchmap.spot.api;

import com.pitchmap.spot.application.SpotNearbyQuery;
import com.pitchmap.spot.domain.SpotType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

// 화면이 쿼리 파라미터로 보내는 반경 검색 조건이다. 빠진 값을 @NotNull 위반으로 돌려주려고 숫자는 래퍼 타입으로 받는다.
// 반경의 상한은 여기서 검사하지 않는다. 50km를 넘는 반경은 입력 형식 오류가 아니라 전용 오류 코드로 알려야 해서,
// 조회 조건(SpotNearbyQuery)을 만들 때 그 쪽이 검사한다.
public record SpotNearbyRequest(
        @NotNull(message = "중심 위도를 입력해야 합니다.")
        @DecimalMin(value = "-90", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90", message = "위도는 90 이하여야 합니다.")
        Double lat,

        @NotNull(message = "중심 경도를 입력해야 합니다.")
        @DecimalMin(value = "-180", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180", message = "경도는 180 이하여야 합니다.")
        Double lng,

        @NotNull(message = "반경을 입력해야 합니다.") @DecimalMin(value = "0", inclusive = false, message = "반경은 0보다 커야 합니다.")
        Double radiusKm,

        Set<@NotNull(message = "장소 유형에 빈 값이 있습니다.") SpotType> types,
        Boolean hasWater,
        Boolean hasToilet,
        Boolean excludeWarning,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    SpotNearbyQuery toQuery() {
        return new SpotNearbyQuery(
                lat,
                lng,
                radiusKm,
                types == null ? Set.of() : types,
                Boolean.TRUE.equals(hasWater),
                Boolean.TRUE.equals(hasToilet),
                Boolean.TRUE.equals(excludeWarning),
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size);
    }
}
