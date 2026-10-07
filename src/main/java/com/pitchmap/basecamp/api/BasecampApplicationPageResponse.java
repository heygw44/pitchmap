package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampApplicationItem;
import com.pitchmap.basecamp.application.BasecampApplicationPage;
import java.time.Instant;
import java.util.List;

/** 캠프 리더가 보는 합류 신청 목록의 한 페이지다. 전체 개수는 없고, 다음 페이지가 있는지만 hasNext로 알린다. 이메일과 출생연도는 담지 않는다. */
public record BasecampApplicationPageResponse(List<ApplicationEntry> content, int page, int size, boolean hasNext) {

    static BasecampApplicationPageResponse from(BasecampApplicationPage page) {
        List<ApplicationEntry> content =
                page.content().stream().map(ApplicationEntry::from).toList();
        return new BasecampApplicationPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record ApplicationEntry(
            long applicationId, String status, String message, Instant appliedAt, ApplicantEntry applicant) {

        static ApplicationEntry from(BasecampApplicationItem item) {
            BasecampApplicationItem.Applicant applicant = item.applicant();
            return new ApplicationEntry(
                    item.applicationId(),
                    item.status(),
                    item.message(),
                    item.appliedAt(),
                    new ApplicantEntry(
                            applicant.memberId(),
                            applicant.nickname(),
                            applicant.ageGroup(),
                            applicant.ageGroupVerified(),
                            applicant.gender(),
                            applicant.genderVerified(),
                            applicant.trustLevel(),
                            applicant.completedCompanions()));
        }
    }

    public record ApplicantEntry(
            long memberId,
            String nickname,
            String ageGroup,
            boolean ageGroupVerified,
            String gender,
            boolean genderVerified,
            int trustLevel,
            int completedCompanions) {}
}
