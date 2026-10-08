package com.pitchmap.admin.api;

import com.pitchmap.program.application.ProgramApplicantItem;
import com.pitchmap.program.application.ProgramApplicantPage;
import java.time.Instant;
import java.util.List;

/** 행사 신청자 목록의 한 페이지다. 전체 개수는 없고, 다음 페이지가 있는지만 hasNext로 알린다. */
public record AdminProgramApplicantPageResponse(List<ApplicantResponse> content, int page, int size, boolean hasNext) {

    static AdminProgramApplicantPageResponse from(ProgramApplicantPage page) {
        List<ApplicantResponse> content =
                page.content().stream().map(ApplicantResponse::from).toList();
        return new AdminProgramApplicantPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    /** 신청 한 건이다. 신청한 회원은 ID와 닉네임만 있고 이메일은 없다. */
    public record ApplicantResponse(
            long applicationId,
            MemberResponse member,
            String status,
            Instant paymentDueAt,
            Instant confirmedAt,
            Instant canceledAt,
            String cancelReason,
            Instant createdAt) {

        static ApplicantResponse from(ProgramApplicantItem item) {
            return new ApplicantResponse(
                    item.applicationId(),
                    new MemberResponse(item.member().memberId(), item.member().nickname()),
                    item.status(),
                    item.paymentDueAt(),
                    item.confirmedAt(),
                    item.canceledAt(),
                    item.cancelReason(),
                    item.createdAt());
        }
    }

    public record MemberResponse(long memberId, String nickname) {}
}
