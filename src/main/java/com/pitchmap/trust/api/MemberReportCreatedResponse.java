package com.pitchmap.trust.api;

import com.pitchmap.trust.domain.ReportStatus;

public record MemberReportCreatedResponse(long reportId, ReportStatus status) {}
