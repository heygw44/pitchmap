package com.pitchmap.spot.application;

/**
 * 박지 제보 요청이다. signalLevel과 groundType은 이름 문자열 그대로 받고, 서비스가 허용 값인지 검증한다. description, signalLevel,
 * groundType은 모르면 null이다.
 */
public record BakjiReportCommand(
        String name,
        double lat,
        double lng,
        String description,
        boolean hasWater,
        boolean hasToilet,
        String signalLevel,
        String groundType) {}
