package com.pitchmap.spot.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 박지 좌표의 공원 경계 경고. 경고가 아니면 areaName 필드를 응답에서 뺀다. */
public record ParkWarningResponse(
        boolean warned,
        @JsonInclude(JsonInclude.Include.NON_NULL) String areaName) {}
