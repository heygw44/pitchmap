package com.pitchmap.program.api;

import com.pitchmap.program.application.ProgramPage;
import com.pitchmap.program.application.ProgramSummary;
import java.time.Instant;
import java.util.List;

/** 공개 행사 목록의 한 페이지다. 전체 개수는 없고, 다음 페이지가 있는지만 hasNext로 알린다. */
public record ProgramPageResponse(List<ProgramSummaryResponse> content, int page, int size, boolean hasNext) {

    static ProgramPageResponse from(ProgramPage page) {
        List<ProgramSummaryResponse> content =
                page.content().stream().map(ProgramSummaryResponse::from).toList();
        return new ProgramPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    /** 행사 한 건이다. status는 UPCOMING, OPEN, CLOSED 중 하나이고, spotId는 지도 장소와 연결하지 않았으면 null이다. */
    public record ProgramSummaryResponse(
            long programId,
            String title,
            String locationText,
            Long spotId,
            Instant startAt,
            Instant endAt,
            Instant applyOpenAt,
            Instant applyCloseAt,
            int capacity,
            int remainingSeats,
            int fee,
            boolean overnight,
            String status) {

        static ProgramSummaryResponse from(ProgramSummary summary) {
            return new ProgramSummaryResponse(
                    summary.programId(),
                    summary.title(),
                    summary.locationText(),
                    summary.spotId(),
                    summary.startAt(),
                    summary.endAt(),
                    summary.applyOpenAt(),
                    summary.applyCloseAt(),
                    summary.capacity(),
                    summary.remainingSeats(),
                    summary.fee(),
                    summary.overnight(),
                    summary.status());
        }
    }
}
