package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampOpenCommand;
import com.pitchmap.basecamp.application.BasecampReviseCommand;
import com.pitchmap.basecamp.domain.Basecamp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 보내지 않은(null) 필드는 현재 값을 그대로 둔다. 정원 범위와 합류 조건 값은 서비스가 검사한다.
public record BasecampReviseRequest(
        @Size(max = Basecamp.TITLE_MAX_LENGTH, message = "제목은 100자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "제목은 공백뿐일 수 없습니다.")
        String title,

        @Size(max = Basecamp.DESCRIPTION_MAX_LENGTH, message = "설명은 2000자 이하여야 합니다.")
        @Pattern(regexp = "(?s).*\\S.*", message = "설명은 공백뿐일 수 없습니다.")
        String description,

        @Schema(description = "보내면 합류 조건 전체를 바꾼다. 열 때와 같은 규칙이다.") @Valid
        BasecampOpenRequest.JoinConditionRequest joinCondition,

        @Schema(description = "캠프 리더를 포함한 정원. 2~6명이고 지금보다 늘리기만 할 수 있다.", example = "5")
        Integer capacity) {

    @Schema(hidden = true)
    @AssertTrue(message = "고칠 필드를 하나 이상 보내야 합니다.")
    public boolean isAnyFieldPresent() {
        return title != null || description != null || joinCondition != null || capacity != null;
    }

    BasecampReviseCommand toCommand() {
        BasecampOpenCommand.JoinConditionCommand condition = joinCondition == null ? null : joinCondition.toCommand();
        return new BasecampReviseCommand(title, description, condition, capacity);
    }
}
