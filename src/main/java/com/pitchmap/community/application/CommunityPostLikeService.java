package com.pitchmap.community.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.community.domain.CommunityPost;
import com.pitchmap.community.domain.CommunityPostRepository;
import com.pitchmap.community.infra.CommunityPostLikeMapper;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 커뮤니티 글에 좋아요를 누르거나 취소한다.
 *
 * <p>글이 없거나 ACTIVE가 아니면(삭제, 숨김, 검토 대기) NOT_FOUND로 거부한다. 이미 누른 글에 다시 누르거나 누르지 않은 글을 취소해도
 * 예외 없이 같은 결과를 돌려주므로, 호출하는 쪽이 같은 요청을 다시 보내도 된다.
 */
@Service
@RequiredArgsConstructor
public class CommunityPostLikeService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostLikeMapper communityPostLikeMapper;
    private final Clock clock;

    /** 호출하면 memberId인 회원의 좋아요를 postId인 글에 남기고, 그 뒤의 상태를 돌려준다. */
    @Transactional
    public CommunityPostLikeResult like(long memberId, long postId) {
        requireActivePost(postId);
        communityPostLikeMapper.insertIgnoringDuplicate(postId, memberId, clock.instant());
        return new CommunityPostLikeResult(true, communityPostLikeMapper.countByPost(postId));
    }

    /** 호출하면 memberId인 회원의 좋아요를 postId인 글에서 지우고, 그 뒤의 상태를 돌려준다. */
    @Transactional
    public CommunityPostLikeResult unlike(long memberId, long postId) {
        requireActivePost(postId);
        communityPostLikeMapper.delete(postId, memberId);
        return new CommunityPostLikeResult(false, communityPostLikeMapper.countByPost(postId));
    }

    private void requireActivePost(long postId) {
        communityPostRepository
                .findById(postId)
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
