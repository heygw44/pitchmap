package com.pitchmap.basecamp.application;

import com.pitchmap.basecamp.infra.MyBasecampMapper;
import com.pitchmap.basecamp.infra.MyBasecampRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인한 회원이 지금 캠프 리더, 멤버, 신청자로 얽혀 있는 베이스캠프를 출발일이 늦은 순서로 읽는다. */
@Service
@RequiredArgsConstructor
public class MyBasecampListService {

    private final MyBasecampMapper myBasecampMapper;

    /**
     * 호출하면 memberId인 회원의 베이스캠프 가운데 query 조건에 맞는 것을 한 페이지 읽는다.
     *
     * <p>다음 페이지가 있는지 알려고 한 건을 더 읽고, 그 건은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public MyBasecampPage list(long memberId, MyBasecampListQuery query) {
        long offset = (long) query.page() * query.size();
        List<MyBasecampRow> rows =
                myBasecampMapper.selectMine(memberId, query.relation(), query.status(), offset, query.size() + 1);
        boolean hasNext = rows.size() > query.size();
        List<MyBasecampItem> content = rows.stream()
                .limit(query.size())
                .map(MyBasecampListService::toItem)
                .toList();
        return new MyBasecampPage(content, query.page(), query.size(), hasNext);
    }

    private static MyBasecampItem toItem(MyBasecampRow row) {
        return new MyBasecampItem(
                row.basecampId(),
                row.title(),
                new MyBasecampItem.SpotSummary(row.spotId(), row.spotName(), row.spotType()),
                row.startDate(),
                row.endDate(),
                row.capacity(),
                row.headcount(),
                row.status().name(),
                row.myRelation().name());
    }
}
