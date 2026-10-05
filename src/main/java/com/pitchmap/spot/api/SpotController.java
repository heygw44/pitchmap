package com.pitchmap.spot.api;

import com.pitchmap.spot.application.SpotMapQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spots")
class SpotController {

    private final SpotMapQueryService spotMapQueryService;

    SpotController(SpotMapQueryService spotMapQueryService) {
        this.spotMapQueryService = spotMapQueryService;
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
}
