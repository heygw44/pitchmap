package com.pitchmap.common.persistence;

import java.time.Instant;

/** MyBatis 조회 결과를 담는 record. MyBatis가 이름으로 매핑하므로, 구성요소 이름이 열 이름(snake_case를 camelCase로 바꾼 것)과 같아야 한다. */
public record DomainProbeView(String domain, String source, Instant createdAt) {}
