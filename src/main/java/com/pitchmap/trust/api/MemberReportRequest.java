package com.pitchmap.trust.api;

import com.pitchmap.trust.application.MemberReportCommand;
import com.pitchmap.trust.domain.MemberReport;
import com.pitchmap.trust.domain.ReportKind;
import com.pitchmap.trust.domain.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 빠진 값을 @NotNull 위반으로 돌려주려고 ID와 종류, 유형은 래퍼 타입으로 받는다. 후기 ID는 회원 신고에서 없어야 하므로 필수가 아니다.
public record MemberReportRequest(
        @NotNull(message = "신고할 회원을 입력해야 합니다.") @Positive(message = "신고할 회원 ID는 양수여야 합니다.")
        Long targetMemberId,

        @NotNull(message = "신고 근거가 되는 베이스캠프를 입력해야 합니다.") @Positive(message = "베이스캠프 ID는 양수여야 합니다.")
        Long basecampId,

        @NotNull(message = "신고 종류를 입력해야 합니다.") ReportKind kind,

        @Positive(message = "동행 후기 ID는 양수여야 합니다.") Long companionReviewId,

        @NotNull(message = "신고 유형을 입력해야 합니다.") ReportType type,

        @NotBlank(message = "신고 내용을 입력해야 합니다.")
        @Size(max = MemberReport.CONTENT_MAX_LENGTH, message = "신고 내용은 1000자 이하여야 합니다.")
        String content) {

    MemberReportCommand toCommand() {
        return new MemberReportCommand(targetMemberId, basecampId, kind, companionReviewId, type, content);
    }
}
