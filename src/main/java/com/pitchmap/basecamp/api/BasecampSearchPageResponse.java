package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.BasecampSearchItem;
import com.pitchmap.basecamp.application.BasecampSearchPage;
import com.pitchmap.basecamp.application.JoinConditionSummary;
import com.pitchmap.basecamp.domain.JoinUnmetReason;
import java.time.LocalDate;
import java.util.List;

/** 베이스캠프 검색 결과의 한 페이지다. 전체 개수는 없고, 다음 페이지가 있는지만 hasNext로 알린다. */
public record BasecampSearchPageResponse(
        List<BasecampSearchItemResponse> content, int page, int size, boolean hasNext) {

    static BasecampSearchPageResponse from(BasecampSearchPage page) {
        List<BasecampSearchItemResponse> content =
                page.content().stream().map(BasecampSearchItemResponse::from).toList();
        return new BasecampSearchPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    /**
     * 검색 결과의 베이스캠프 한 건이다. 비로그인 요청자에게는 신청 가능 여부(canApply, unmetReasons)가 의미 없어서 필드 자체를 뺀다.
     * 로그인한 요청자에게는 둘 다 담고, 신청할 수 있으면 unmetReasons는 빈 목록이다.
     */
    public sealed interface BasecampSearchItemResponse {

        static BasecampSearchItemResponse from(BasecampSearchItem item) {
            SpotResponse spot = new SpotResponse(
                    item.spot().spotId(),
                    item.spot().name(),
                    item.spot().type(),
                    item.spot().lat(),
                    item.spot().lng());
            if (item.eligibility() == null) {
                return new Anonymous(
                        item.basecampId(),
                        item.title(),
                        spot,
                        item.startDate(),
                        item.endDate(),
                        item.capacity(),
                        item.headcount(),
                        item.status(),
                        item.joinCondition());
            }
            return new Member(
                    item.basecampId(),
                    item.title(),
                    spot,
                    item.startDate(),
                    item.endDate(),
                    item.capacity(),
                    item.headcount(),
                    item.status(),
                    item.joinCondition(),
                    item.eligibility().canApply(),
                    item.eligibility().unmetReasons());
        }
    }

    /** 비로그인 요청자가 보는 항목이다. */
    public record Anonymous(
            long basecampId,
            String title,
            SpotResponse spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            String status,
            JoinConditionSummary joinCondition)
            implements BasecampSearchItemResponse {}

    /** 로그인한 요청자가 보는 항목이다. */
    public record Member(
            long basecampId,
            String title,
            SpotResponse spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            String status,
            JoinConditionSummary joinCondition,
            boolean canApply,
            List<JoinUnmetReason> unmetReasons)
            implements BasecampSearchItemResponse {}

    /** 장소 요약이다. type은 장소 유형 이름이다. */
    public record SpotResponse(long spotId, String name, String type, double lat, double lng) {}
}
