package com.pitchmap.basecamp.api;

import com.pitchmap.basecamp.application.MyBasecampItem;
import com.pitchmap.basecamp.application.MyBasecampPage;
import java.time.LocalDate;
import java.util.List;

/** 내 베이스캠프 목록의 한 페이지다. 전체 개수는 없고, 다음 페이지가 있는지만 hasNext로 알린다. */
public record MyBasecampPageResponse(List<Entry> content, int page, int size, boolean hasNext) {

    static MyBasecampPageResponse from(MyBasecampPage page) {
        List<Entry> content = page.content().stream().map(Entry::from).toList();
        return new MyBasecampPageResponse(content, page.page(), page.size(), page.hasNext());
    }

    public record Entry(
            long basecampId,
            String title,
            SpotEntry spot,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            int headcount,
            String status,
            String myRelation) {

        static Entry from(MyBasecampItem item) {
            return new Entry(
                    item.basecampId(),
                    item.title(),
                    new SpotEntry(
                            item.spot().spotId(),
                            item.spot().name(),
                            item.spot().type()),
                    item.startDate(),
                    item.endDate(),
                    item.capacity(),
                    item.headcount(),
                    item.status(),
                    item.myRelation());
        }
    }

    /** 장소 요약이다. type은 장소 유형 이름이다. */
    public record SpotEntry(long spotId, String name, String type) {}
}
