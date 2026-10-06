package com.pitchmap.member.domain;

import java.time.Instant;

/**
 * 어떤 기간 안에 인증 코드를 발송한 횟수와 가장 오래된 발송 시각.
 *
 * @param count 기간 안에 발송한 횟수
 * @param oldestSentAt 기간 안에서 가장 오래된 발송 시각. 발송이 없으면 null이다.
 */
public record SendWindow(long count, Instant oldestSentAt) {}
