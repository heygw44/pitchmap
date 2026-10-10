package com.pitchmap.community.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.community.domain.CommunityPost;
import com.pitchmap.community.infra.CommunityPostListCondition;
import com.pitchmap.community.infra.CommunityPostMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 커뮤니티 글 목록과 상세를 읽는다. ACTIVE인 글만 읽을 수 있고, 삭제·숨김·검토 대기 글은 없는 글로 본다. */
@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {

    private final CommunityPostMapper communityPostMapper;
    private final CommunityPostItemReader communityPostItemReader;

    /**
     * 호출하면 query 조건에 맞는 ACTIVE인 글을 최신순으로 한 페이지 돌려준다.
     *
     * <p>spotId로 거를 때는 그 장소가 지금 ACTIVE인지 보지 않는다. 화면이 페이지 번호를 그리려면 전체 글 수가 필요하다. 그래서 서비스는 같은
     * 조건으로 글 수를 따로 센다. 마지막 페이지 너머를 요청하면 빈 목록과 함께 전체 글 수와 페이지 수를 돌려준다.
     */
    @Transactional(readOnly = true)
    public CommunityPostPage list(CommunityPostListQuery query, int page, int size) {
        CommunityPostListCondition condition = toCondition(query);
        long totalElements = communityPostMapper.countActive(condition);
        long offset = (long) page * size;
        List<CommunityPostItem> content = offset >= totalElements
                ? List.of()
                : communityPostMapper.selectActive(condition, offset, size).stream()
                        .map(communityPostItemReader::toListItem)
                        .toList();
        return CommunityPostPage.of(content, page, size, totalElements);
    }

    /**
     * 호출하면 postId인 글 한 건을 본문 전체와 이미지 목록과 함께 돌려준다. viewerId가 null이 아니면 그 회원이 좋아요를 눌렀는지(likedByMe)도 담는다.
     * 글이 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다.
     *
     * <p>조회한 사람이 작성자가 아니면(비회원 포함) 조회수를 1 올리고, 올린 값을 응답에 담는다. 작성자가 자기 글을 열 때는 세지 않는다. 같은 사람이
     * 다시 열어도 그때마다 센다.
     */
    @Transactional
    public CommunityPostItem detail(long postId, Long viewerId) {
        CommunityPostItem item = communityPostItemReader
                .findDetail(postId, viewerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (viewerId != null && viewerId == item.authorId()) {
            return item;
        }
        // 읽은 뒤 글이 숨겨졌으면 0행이 바뀐다. 이때는 이미 읽은 글을 그대로 돌려준다.
        if (communityPostMapper.incrementViewCount(postId) == 0) {
            return item;
        }
        return item.withOneMoreView();
    }

    private static CommunityPostListCondition toCondition(CommunityPostListQuery query) {
        Integer minLikeCount = query.popular() ? CommunityPost.POPULAR_LIKE_THRESHOLD : null;
        if (query.keyword() == null) {
            return new CommunityPostListCondition(query.spotId(), minLikeCount, null, null);
        }
        return new CommunityPostListCondition(
                query.spotId(), minLikeCount, query.searchType().name(), escapeLike(query.keyword()));
    }

    // LIKE에서 %와 _는 와일드카드이고 \는 그 둘을 글자로 만드는 표시다. 회원이 입력한 이 글자들을 글자 그대로 찾도록 앞에 \를 붙인다.
    // \를 먼저 바꿔야 뒤에서 붙인 \가 다시 바뀌지 않는다.
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
