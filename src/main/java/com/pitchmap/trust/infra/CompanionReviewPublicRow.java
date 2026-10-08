package com.pitchmap.trust.infra;

import java.time.Instant;

/** 다른 회원에게 보이는 받은 후기 한 건. 작성자는 읽지 않는다. tags는 태그 이름을 쉼표로 이은 문자열이고 태그가 없으면 null이다. */
public record CompanionReviewPublicRow(String tags, String comment, Instant createdAt) {}
