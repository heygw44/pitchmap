package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampApplyCommand;
import com.pitchmap.basecamp.domain.BasecampApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record BasecampApplyRequest(
        @Schema(description = "캠프 리더에게 전하는 말. 생략할 수 있다.")
        @Size(max = BasecampApplication.MESSAGE_MAX_LENGTH, message = "신청 메시지는 500자 이하여야 합니다.")
        String message) {

    BasecampApplyCommand toCommand(long basecampId, long memberId) {
        return new BasecampApplyCommand(basecampId, memberId, message);
    }

    /** 요청 본문이 아예 없을 때 쓰는 값이다. */
    static BasecampApplyRequest empty() {
        return new BasecampApplyRequest(null);
    }
}
