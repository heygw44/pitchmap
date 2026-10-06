package com.pitchmap.spot.domain;

/** 장소의 상태. 서버는 장소 행을 지우지 않고 이 값으로 지도에서 숨긴다. */
public enum SpotStatus {
    /** 지도에 보이는 장소. */
    ACTIVE,
    /** 신고가 쌓여 관리자 검토를 기다리는 박지. */
    PENDING_REVIEW,
    /** 관리자가 숨겼거나, 원천 데이터에서 사라져 동기화가 숨긴 장소. */
    HIDDEN,
    /** 제보자가 삭제한 박지. */
    DELETED
}
