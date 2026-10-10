package com.pitchmap.community.infra;

/** 이미지 조회가 읽은 이미지 한 장의 ID와 저장소 객체 키. 키는 서버 안에서만 쓰고 응답에 내보내지 않는다. */
public record CommunityImageRow(long imageId, String objectKey) {}
