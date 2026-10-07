package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.domain.KickReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;

// 강퇴 사유는 문자열로 받고 허용 값인지 직접 검사한다. 모르는 값을 Jackson의 enum 변환 오류가 아니라 필드 오류가 담긴 입력 오류로 돌려주려는 것이다.
public record BasecampKickRequest(
        @Schema(implementation = KickReason.class, description = "강퇴 사유.") @NotNull(message = "강퇴 사유를 입력해야 합니다.")
        String reason) {

    @AssertTrue(message = "강퇴 사유는 NO_CONTACT, CONDITION_MISMATCH, INAPPROPRIATE_BEHAVIOR, OTHER 중 하나여야 합니다.")
    public boolean isReasonValid() {
        return reason == null
                || Arrays.stream(KickReason.values())
                        .anyMatch(candidate -> candidate.name().equals(reason));
    }

    KickReason toReason() {
        return KickReason.valueOf(reason);
    }
}
