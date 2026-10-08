package com.pitchmap.basecamp.infra;

import com.pitchmap.basecamp.domain.BasecampApplicationStatus;
import java.time.Instant;

/** 캠프 리더가 보는 신청 목록의 한 행이다. 신청자의 프로필은 담지 않고 ID만 담는다. */
public record BasecampApplicationRow(
        long applicationId, long applicantId, BasecampApplicationStatus status, String message, Instant appliedAt) {}
