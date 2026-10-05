package com.pitchmap.publicdata.infra;

/**
 * 국립자연휴양림 예약 정책 파일의 한 행에서 장소 적재에 쓰는 열만 담는다. 지역 열과 정책 관련 열은 담지 않는다.
 *
 * <p>값은 파일에 적힌 문자열 그대로다. 그래서 앞뒤 공백 제거, 숫자 변환, 빈 값 처리는 이 값을 쓰는 쪽이 한다.
 *
 * @param institutionId 기관아이디. 휴양림마다 하나이고 예약 정책 행마다 반복된다.
 * @param name 기관명
 * @param latitude 위도
 * @param longitude 경도
 * @param phone 연락처
 * @param homepage 홈페이지주소
 */
public record ForestRecord(
        String institutionId, String name, String latitude, String longitude, String phone, String homepage) {}
