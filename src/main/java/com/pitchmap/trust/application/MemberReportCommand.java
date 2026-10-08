package com.pitchmap.trust.application;

import com.pitchmap.trust.domain.ReportKind;
import com.pitchmap.trust.domain.ReportType;

/** 회원이나 동행 후기를 신고하려는 요청. companionReviewId는 후기 신고일 때만 있다. */
public record MemberReportCommand(
        long targetMemberId,
        long basecampId,
        ReportKind kind,
        Long companionReviewId,
        ReportType type,
        String content) {}
