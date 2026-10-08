package com.pitchmap.program.application;

import java.time.Instant;

/** 관리자가 보는 신청 한 건이다. 신청한 회원은 ID와 닉네임만 담고 이메일은 담지 않는다. */
public record ProgramApplicantItem(
        long applicationId,
        Applicant member,
        String status,
        Instant paymentDueAt,
        Instant confirmedAt,
        Instant canceledAt,
        String cancelReason,
        Instant createdAt) {

    public record Applicant(long memberId, String nickname) {}
}
