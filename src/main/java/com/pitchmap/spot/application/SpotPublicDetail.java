package com.pitchmap.spot.application;

import java.time.LocalDate;

/**
 * 야영장과 자연휴양림의 상세. 값은 공공데이터 원천이 준 그대로이고, 원천에 없으면 {@code null}이다.
 *
 * <p>sourceDate는 원천 데이터의 기준일이다. 파일로 받는 자연휴양림에만 있고, 기준일이 없는 고캠핑 야영장은 {@code null}이다. facilities는 시설 정보가
 * 하나도 없으면 {@code null}이다.
 *
 * <p>closedNow는 조회한 날의 한국 날짜에 휴장 중인지를 뜻한다. 서비스는 휴장 기간이 있으면 기간을 보고, 기간이 없을 때만 운영 상태를 본다.
 */
public record SpotPublicDetail(
        PublicSpotSource source,
        String category,
        SpotFacilities facilities,
        String phone,
        String homepage,
        LocalDate sourceDate,
        PublicSpotOperatingStatus operatingStatus,
        LocalDate closedFrom,
        LocalDate closedUntil,
        boolean closedNow) {}
