package com.pitchmap.spot.api;

import com.pitchmap.spot.application.SpotDetailQueryService;
import com.pitchmap.spot.application.SpotMapQueryService;
import com.pitchmap.spot.application.SpotNearbyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spots")
class SpotController {

    private final SpotMapQueryService spotMapQueryService;
    private final SpotNearbyQueryService spotNearbyQueryService;
    private final SpotDetailQueryService spotDetailQueryService;

    SpotController(
            SpotMapQueryService spotMapQueryService,
            SpotNearbyQueryService spotNearbyQueryService,
            SpotDetailQueryService spotDetailQueryService) {
        this.spotMapQueryService = spotMapQueryService;
        this.spotNearbyQueryService = spotNearbyQueryService;
        this.spotDetailQueryService = spotDetailQueryService;
    }

    @Operation(
            summary = "지도 영역 조회",
            description = "남서쪽 좌표(swLat, swLng)와 북동쪽 좌표(neLat, neLng)로 정한 화면 영역 안의 장소를 돌려준다. "
                    + "경계선 위의 장소도 포함하고, 로그인하지 않아도 조회할 수 있다. "
                    + "위도는 -90~90, 경도는 -180~180이고 남서쪽 값이 북동쪽 값보다 작아야 한다. "
                    + "zoom은 카카오맵 레벨 1~14이며, 서버는 범위만 검증하고 계산에는 쓰지 않는다. "
                    + "types에 CAMPSITE, FOREST, BAKJI를 쉼표로 구분해 주면 그 유형만 돌려주고, 주지 않으면 모든 유형을 돌려준다. "
                    + "hasWater나 hasToilet이 true이면 서버는 해당 시설이 있는 박지만 남기고, 야영장과 자연휴양림은 이 값과 상관없이 포함한다. "
                    + "excludeWarning이 true이면 공원 경계 경고가 붙은 장소를 뺀다. "
                    + "조건에 맞는 장소가 500개 이하이면 모두 markers로 주고 clusters는 빈 배열로 준다. "
                    + "500개를 넘으면 markers를 비우고, 화면 영역을 가로세로 20칸씩 나눠 장소가 있는 칸마다 "
                    + "장소 수(count)와 칸 안 장소들의 평균 좌표(lat, lng)를 clusters로 준다. "
                    + "closedNow는 서버가 오늘 한국 날짜로 계산한 휴장 여부다. 원천의 휴장 정보가 정확하지 않아서 "
                    + "서버는 휴장 중인 장소도 숨기지 않고 표시만 한다. 박지의 closedNow는 항상 false다. "
                    + "파라미터가 빠졌거나 범위를 벗어났거나, 남서쪽 값이 북동쪽 값보다 작지 않거나, 모르는 유형이면 "
                    + "400 INVALID_INPUT으로 응답한다.")
    @GetMapping
    SpotAreaResponse findInArea(@Valid @ParameterObject @ModelAttribute SpotAreaRequest request) {
        return SpotAreaResponse.from(spotMapQueryService.findInArea(request.toQuery()));
    }

    @Operation(
            summary = "반경 검색",
            description = "중심 좌표(lat, lng)에서 반경 radiusKm 안에 있는 장소를 가까운 순서로 돌려준다. "
                    + "로그인하지 않아도 조회할 수 있다. "
                    + "distanceKm는 중심에서 장소까지의 거리(km)이고, 서버가 소수 둘째 자리까지 반올림해서 준다. "
                    + "위도는 -90~90, 경도는 -180~180이고, 반경은 0보다 크고 50 이하여야 한다. "
                    + "반경이 50을 넘으면 400 SPOT_RADIUS_TOO_LARGE로 응답한다. "
                    + "types, hasWater, hasToilet, excludeWarning은 지도 영역 조회와 같다. 예를 들어 hasWater가 true이면 "
                    + "서버는 물이 있는 박지만 남기고, 야영장과 자연휴양림은 이 값과 상관없이 포함한다. "
                    + "closedNow는 서버가 오늘 한국 날짜로 계산한 휴장 여부다. 원천의 휴장 정보가 정확하지 않아서 "
                    + "서버는 휴장 중인 장소도 숨기지 않고 표시만 한다. 박지의 closedNow는 항상 false다. "
                    + "page는 0부터 시작하고 기본값은 0이다. size는 1~50이고 기본값은 20이다. "
                    + "응답에는 전체 개수가 없고, 다음 페이지가 있는지만 hasNext로 알려 준다. "
                    + "그 밖에 파라미터가 빠졌거나 범위를 벗어났거나 모르는 유형이면 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/nearby")
    SpotNearbyPageResponse findNearby(@Valid @ParameterObject @ModelAttribute SpotNearbyRequest request) {
        return SpotNearbyPageResponse.from(spotNearbyQueryService.findNearby(request.toQuery()));
    }

    @Operation(
            summary = "장소 상세",
            description = "장소 하나의 기본 정보와 유형별 상세를 돌려준다. 로그인하지 않아도 조회할 수 있다. "
                    + "박지(BAKJI)이면 서버는 bakji에 설명, 물·화장실 유무, 통신 신호, 다녀온 회원의 확인 수, "
                    + "제보자(memberId, nickname)를 채우고 publicDetail은 null로 준다. "
                    + "야영장(CAMPSITE)과 자연휴양림(FOREST)이면 publicDetail에 원천, 분류, 시설, 연락처, 원천 기준일, "
                    + "운영 상태, 휴장 기간을 채우고 bakji는 null로 준다. 원천에 시설 정보가 없으면 facilities는 null이다. "
                    + "closedNow는 서버가 오늘 한국 날짜로 계산한 휴장 여부다. 원천의 휴장 정보가 정확하지 않아서 "
                    + "서버는 휴장 중인 장소도 숨기지 않고 표시만 한다. "
                    + "parkWarning.warned는 장소가 공원 경계 안일 가능성이 있는지를 뜻한다. 경고가 아니면 warned만 주고, "
                    + "경고이면 공원 이름(areaName), 경계 데이터 출처(source)와 기준일(sourceDate), 참고용 고지(notice), "
                    + "안내(guide)를 함께 준다. 공원 경계 행이 없는 경고에는 areaName, source, sourceDate가 빠진다. "
                    + "weather에는 기상청 단기·중기 예보와 오늘(한국 날짜) 출몰시각(sun)을 준다. "
                    + "기상청 호출이 실패하거나 늦으면 weather는 null이고 나머지 필드는 정상으로 응답한다. 이때 천문연은 부르지 않는다. "
                    + "기상청은 성공하고 천문연만 실패하면 weather는 채우고 sun만 null로 준다. "
                    + "아직 채우지 않는 항목이 있어서, rating은 average가 null이고 count가 0이며, "
                    + "recentReviews, expectedPeople, recruitingBasecamps는 빈 배열로 준다. "
                    + "장소가 없거나 숨김, 삭제, 검토 대기 상태이면 404 NOT_FOUND로 응답한다. "
                    + "spotId가 숫자가 아니면 400 INVALID_INPUT으로 응답한다.")
    @GetMapping("/{spotId}")
    SpotDetailResponse findDetail(@PathVariable long spotId) {
        return SpotDetailResponse.from(spotDetailQueryService.findDetail(spotId));
    }
}
