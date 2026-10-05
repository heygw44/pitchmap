package com.pitchmap.publicdata.infra;

/**
 * 고캠핑 API가 돌려주는 캠핑장 한 건. 원천 필드 81개 가운데 동기화에 쓰는 것만 원천 이름 그대로 담는다.
 *
 * <p>원천은 모든 값을 문자열로 준다. 값이 없으면 빈 문자열이 오고, 응답에 필드가 아예 없으면 {@code null}이다. 그래서 숫자 변환과 빈 값
 * 처리는 이 값을 쓰는 쪽이 한다. 원천에서 {@code mapX}는 경도, {@code mapY}는 위도다.
 */
public record GoCampingItem(
        String contentId,
        String facltNm,
        String addr1,
        String addr2,
        String mapX,
        String mapY,
        String induty,
        String tel,
        String homepage,
        String toiletCo,
        String swrmCo,
        String wtrplCo,
        String brazierCl,
        String sbrsCl,
        String sbrsEtc,
        String posblFcltyCl,
        String posblFcltyEtc,
        String animalCmgCl) {}
