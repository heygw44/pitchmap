package com.pitchmap.program.api;

import com.pitchmap.program.application.MyProgramApplicationItem;
import com.pitchmap.program.application.MyProgramApplicationPage;
import java.time.Instant;
import java.util.List;

/** 내 행사 신청 목록의 한 페이지다. 전체 개수는 없고, 다음 페이지가 있는지만 hasNext로 알린다. 해당하지 않는 시각과 사유는 null로 내보낸다. */
public record MyProgramApplicationPageResponse(List<ItemResponse> content, int page, int size, boolean hasNext) {

    static MyProgramApplicationPageResponse from(MyProgramApplicationPage page) {
        List<ItemResponse> content =
                page.content().stream().map(ItemResponse::from).toList();
        return new MyProgramApplicationPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record ItemResponse(
            long applicationId,
            String status,
            Instant paymentDueAt,
            Instant confirmedAt,
            Instant canceledAt,
            String cancelReason,
            Instant createdAt,
            ProgramResponse program) {

        static ItemResponse from(MyProgramApplicationItem item) {
            MyProgramApplicationItem.Program program = item.program();
            return new ItemResponse(
                    item.applicationId(),
                    item.status(),
                    item.paymentDueAt(),
                    item.confirmedAt(),
                    item.canceledAt(),
                    item.cancelReason(),
                    item.createdAt(),
                    new ProgramResponse(
                            program.programId(),
                            program.title(),
                            program.locationText(),
                            program.startAt(),
                            program.endAt(),
                            program.fee(),
                            program.status()));
        }
    }

    public record ProgramResponse(
            long programId,
            String title,
            String locationText,
            Instant startAt,
            Instant endAt,
            int fee,
            String status) {}
}
