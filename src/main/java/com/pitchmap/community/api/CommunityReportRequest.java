package com.pitchmap.community.api;

import com.pitchmap.community.application.CommunityReportCommand;
import com.pitchmap.community.domain.CommunityReport;
import com.pitchmap.community.domain.CommunityReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 신고 사유는 문자열로 받고 서비스가 허용 값인지 검증한다. 모르는 값을 Jackson의 enum 변환 오류가 아니라 서비스의 입력 오류로 돌려주려는 것이다.
public record CommunityReportRequest(
        @Schema(implementation = CommunityReportReason.class, description = "신고 사유.")
        @NotNull(message = "신고 사유를 입력해야 합니다.")
        String reason,

        @Size(max = CommunityReport.CONTENT_MAX_LENGTH, message = "신고 내용은 1000자 이하여야 합니다.")
        String content) {

    CommunityReportCommand toCommand() {
        return new CommunityReportCommand(reason, content);
    }
}
