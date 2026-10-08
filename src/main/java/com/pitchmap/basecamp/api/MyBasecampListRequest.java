package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.MyBasecampListQuery;
import com.pitchmap.basecamp.domain.BasecampRelation;
import com.pitchmap.basecamp.domain.BasecampStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;

// 관계와 상태는 문자열로 받고 허용 값인지 직접 검사한다. 모르는 값을 Spring의 enum 변환 오류가 아니라 필드 오류가 담긴 입력 오류로 돌려주려는 것이다.
// 생략하면 그 조건으로는 거르지 않는다. 관계에는 NONE이 있지만 목록에서는 의미가 없어서 허용하지 않는다.
public record MyBasecampListRequest(
        @Schema(description = "내 관계. LEADER, MEMBER, APPLICANT 중 하나. 생략하면 셋 모두.")
        String relation,

        @Schema(implementation = BasecampStatus.class, description = "베이스캠프 상태. 생략하면 모든 상태.")
        String status,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다.")
        Integer size) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    @AssertTrue(message = "relation은 LEADER, MEMBER, APPLICANT 중 하나여야 합니다.")
    public boolean isRelationValid() {
        return relation == null || (!BasecampRelation.NONE.name().equals(relation) && isRelationName(relation));
    }

    @AssertTrue(message = "status는 RECRUITING, CLOSED, CONFIRMED, COMPLETED, CANCELED 중 하나여야 합니다.")
    public boolean isStatusValid() {
        return status == null
                || Arrays.stream(BasecampStatus.values())
                        .anyMatch(candidate -> candidate.name().equals(status));
    }

    MyBasecampListQuery toQuery() {
        return new MyBasecampListQuery(
                relation == null ? null : BasecampRelation.valueOf(relation),
                status == null ? null : BasecampStatus.valueOf(status),
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size);
    }

    private static boolean isRelationName(String value) {
        return Arrays.stream(BasecampRelation.values())
                .anyMatch(candidate -> candidate.name().equals(value));
    }
}
