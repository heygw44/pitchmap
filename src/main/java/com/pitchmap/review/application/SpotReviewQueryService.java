package com.pitchmap.review.application;

import com.pitchmap.review.infra.SpotReviewMapper;
import com.pitchmap.review.infra.SpotReviewRow;
import com.pitchmap.spot.application.ActiveSpotChecker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장소의 후기 목록을 읽는다. 지도에 보이는(ACTIVE) 장소의 후기만 읽을 수 있다. */
@Service
@RequiredArgsConstructor
public class SpotReviewQueryService {

    private final ActiveSpotChecker activeSpotChecker;
    private final SpotReviewMapper spotReviewMapper;

    /**
     * 호출하면 spotId인 장소의 후기를 작성 시각이 늦은 순서로 한 페이지 돌려준다. 작성 시각이 같으면 후기 ID가 큰 것이 먼저 나온다.
     *
     * <p>장소가 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다. 서비스는 다음 페이지가 있는지 알려고 한 행을 더 읽고, 그 행은 결과에서 뺀다.
     * 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public SpotReviewPage list(long spotId, int page, int size) {
        activeSpotChecker.requireActive(spotId);
        long offset = (long) page * size;
        List<SpotReviewRow> rows = spotReviewMapper.selectBySpot(spotId, offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<SpotReviewItem> content =
                rows.stream().limit(size).map(SpotReviewItem::from).toList();
        return new SpotReviewPage(content, page, size, hasNext);
    }
}
