package com.pitchmap.trust.api;

import com.pitchmap.trust.application.CompanionReviewWriteCommand;
import com.pitchmap.trust.domain.CompanionReview;
import com.pitchmap.trust.domain.CompanionReviewTag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

// 빠진 값을 @NotNull 위반으로 돌려주려고 상대와 "다시 동행" 여부는 래퍼 타입으로 받는다. 태그는 없거나 비어 있어도 된다.
public record CompanionReviewWriteRequest(
        @NotNull(message = "후기를 받을 회원을 입력해야 합니다.") @Positive(message = "후기를 받을 회원 ID는 양수여야 합니다.")
        Long revieweeId,

        @NotNull(message = "다시 동행하고 싶은지 입력해야 합니다.") Boolean rejoinWanted,

        @Size(max = CompanionReviewWriteRequest.MAX_TAGS, message = "태그는 7개 이하여야 합니다.")
        @UniqueElements(message = "같은 태그를 두 번 넣을 수 없습니다.")
        List<@NotNull(message = "태그 값이 비어 있습니다.") CompanionReviewTag> tags,

        @Size(max = CompanionReview.COMMENT_MAX_LENGTH, message = "코멘트는 300자 이하여야 합니다.")
        String comment) {

    static final int MAX_TAGS = 7;

    CompanionReviewWriteCommand toCommand() {
        return new CompanionReviewWriteCommand(revieweeId, rejoinWanted, tags == null ? List.of() : tags, comment);
    }
}
