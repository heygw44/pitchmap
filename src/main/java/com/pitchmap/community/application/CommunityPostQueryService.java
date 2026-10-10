package com.pitchmap.community.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.community.domain.CommunityCategory;
import com.pitchmap.community.infra.CommunityPostMapper;
import com.pitchmap.community.infra.CommunityPostRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 커뮤니티 글 목록과 상세를 읽는다. ACTIVE인 글만 읽을 수 있고, 삭제·숨김·검토 대기 글은 없는 글로 본다. */
@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {

    private final CommunityPostMapper communityPostMapper;

    /**
     * 호출하면 ACTIVE인 글을 최신순으로 한 페이지 돌려준다. category와 spotId는 null이면 거르지 않는다.
     *
     * <p>spotId로 거를 때는 그 장소가 지금 ACTIVE인지 보지 않는다. 서비스는 다음 페이지가 있는지 알려고 한 행을 더 읽고, 그 행은 결과에서
     * 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public CommunityPostPage list(CommunityCategory category, Long spotId, int page, int size) {
        long offset = (long) page * size;
        String categoryName = category == null ? null : category.name();
        List<CommunityPostRow> rows = communityPostMapper.selectActive(categoryName, spotId, offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<CommunityPostItem> content =
                rows.stream().limit(size).map(CommunityPostItem::from).toList();
        return new CommunityPostPage(content, page, size, hasNext);
    }

    /**
     * 호출하면 postId인 글 한 건을 본문 전체와 함께 돌려준다. viewerId가 null이 아니면 그 회원이 좋아요를 눌렀는지(likedByMe)도 담는다.
     * 글이 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다.
     */
    @Transactional(readOnly = true)
    public CommunityPostItem detail(long postId, Long viewerId) {
        return communityPostMapper
                .selectActiveById(postId, viewerId)
                .map(CommunityPostItem::from)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
