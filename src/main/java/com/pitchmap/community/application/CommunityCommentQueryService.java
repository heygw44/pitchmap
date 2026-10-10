package com.pitchmap.community.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.community.domain.CommunityPost;
import com.pitchmap.community.domain.CommunityPostRepository;
import com.pitchmap.community.infra.CommunityCommentMapper;
import com.pitchmap.community.infra.CommunityCommentRow;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 커뮤니티 글의 댓글 목록을 읽는다. 글이 ACTIVE일 때만 읽을 수 있고, 아니면 없는 글로 본다. */
@Service
@RequiredArgsConstructor
public class CommunityCommentQueryService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentMapper communityCommentMapper;

    /**
     * 호출하면 postId인 글의 댓글을 오래된 순으로 한 페이지 돌려준다. page와 size는 답글이 아닌 댓글 단위로 센다.
     *
     * <p>서비스는 다음 페이지가 있는지 알려고 한 행을 더 읽고, 그 행은 결과에서 뺀다. 그다음 읽은 댓글들의 ACTIVE 답글을 한 번에 읽어
     * 부모 아래에 묶는다. 읽은 댓글이 없으면 답글 쿼리를 보내지 않는다. 빈 IN 목록은 SQL 오류이기 때문이다.
     * 글이 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다.
     */
    @Transactional(readOnly = true)
    public CommunityCommentPage list(long postId, int page, int size) {
        requireActivePost(postId);
        long offset = (long) page * size;
        List<CommunityCommentRow> rows = communityCommentMapper.selectTopLevel(postId, offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<CommunityCommentRow> parents = rows.stream().limit(size).toList();
        return new CommunityCommentPage(toItems(parents), page, size, hasNext);
    }

    private void requireActivePost(long postId) {
        communityPostRepository
                .findById(postId)
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private List<CommunityCommentItem> toItems(List<CommunityCommentRow> parents) {
        if (parents.isEmpty()) {
            return List.of();
        }
        List<Long> parentIds =
                parents.stream().map(CommunityCommentRow::commentId).toList();
        Map<Long, List<CommunityCommentItem>> repliesByParent =
                communityCommentMapper.selectActiveReplies(parentIds).stream()
                        .collect(Collectors.groupingBy(
                                CommunityCommentRow::parentId,
                                Collectors.mapping(
                                        row -> CommunityCommentItem.from(row, List.of()), Collectors.toList())));
        return parents.stream()
                .map(row -> CommunityCommentItem.from(row, repliesByParent.getOrDefault(row.commentId(), List.of())))
                .toList();
    }
}
