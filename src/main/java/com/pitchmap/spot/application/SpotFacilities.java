package com.pitchmap.spot.application;

/**
 * 공공데이터 장소의 시설 정보. 값은 원천 문자열 그대로이고, 원천에 없는 항목은 {@code null}이다.
 *
 * <p>각 항목은 고캠핑 원천 항목 하나에서 온다. toiletCount는 화장실 수(toiletCo), showerCount는 샤워실 수(swrmCo), sinkCount는 개수대
 * 수(wtrplCo), brazier는 화로대(brazierCl), amenities와 amenitiesEtc는 부대시설(sbrsCl, sbrsEtc), nearbyFacilities와
 * nearbyFacilitiesEtc는 주변 이용 가능 시설(posblFcltyCl, posblFcltyEtc), petPolicy는 반려동물 출입(animalCmgCl)이다.
 */
public record SpotFacilities(
        String toiletCount,
        String showerCount,
        String sinkCount,
        String brazier,
        String amenities,
        String amenitiesEtc,
        String nearbyFacilities,
        String nearbyFacilitiesEtc,
        String petPolicy) {}
