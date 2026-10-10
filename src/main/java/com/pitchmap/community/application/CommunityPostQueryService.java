package com.pitchmap.community.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
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
     * 호출하면 ACTIVE인 글을 최신순으로 한 페이지 돌려준다. spotId는 null이면 거르지 않는다.
     *
     * <p>spotId로 거를 때는 그 장소가 지금 ACTIVE인지 보지 않는다. 화면이 페이지 번호를 그리려면 전체 글 수가 필요하다. 그래서 서비스는 같은
     * 조건으로 글 수를 따로 센다. 마지막 페이지 너머를 요청하면 빈 목록과 함께 전체 글 수와 페이지 수를 돌려준다.
     */
    @Transactional(readOnly = true)
    public CommunityPostPage list(Long spotId, int page, int size) {
        long totalElements = communityPostMapper.countActive(spotId);
        long offset = (long) page * size;
        List<CommunityPostItem> content = offset >= totalElements
                ? List.of()
                : communityPostMapper.selectActive(spotId, offset, size).stream()
                        .map(communityPostItemReader::toListItem)
                        .toList();
        return CommunityPostPage.of(content, page, size, totalElements);
    }

    /**
     * 호출하면 postId인 글 한 건을 본문 전체와 이미지 목록과 함께 돌려준다. viewerId가 null이 아니면 그 회원이 좋아요를 눌렀는지(likedByMe)도 담는다.
     * 글이 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다.
     */
    @Transactional(readOnly = true)
    public CommunityPostItem detail(long postId, Long viewerId) {
        return communityPostItemReader
                .findDetail(postId, viewerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
