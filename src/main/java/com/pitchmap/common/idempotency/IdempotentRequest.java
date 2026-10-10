package com.pitchmap.common.idempotency;

/**
 * 멱등성 처리를 요청하는 쪽이 넘기는 값.
 *
 * @param memberId 요청한 회원 ID
 * @param key {@code Idempotency-Key} 헤더 원값. 헤더가 없으면 null
 * @param operation 요청을 구분하는 문자열. 예: {@code "POST /api/programs/12/applications"}
 * @param payload 요청 본문 객체. 본문이 없으면 null
 */
public record IdempotentRequest(long memberId, String key, String operation, Object payload) {}
