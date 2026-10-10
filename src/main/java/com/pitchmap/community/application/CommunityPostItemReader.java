package com.pitchmap.community.application;

import com.pitchmap.community.infra.CommunityImageMapper;
import com.pitchmap.community.infra.CommunityPostMapper;
import com.pitchmap.community.infra.CommunityPostRow;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DB에서 읽은 글 행을 응답용 {@link CommunityPostItem}으로 만든다. 이미지는 저장소 객체 키로 읽어서, 응답마다 새로 서명한 조회 URL로 바꾼다.
 * 서명은 서버 안에서 계산하고 저장소를 부르지 않는다. 호출하는 쪽이 읽기 트랜잭션을 연다.
 */
@Component
@RequiredArgsConstructor
class CommunityPostItemReader {

    private final CommunityPostMapper communityPostMapper;
    private final CommunityImageMapper communityImageMapper;
    private final CommunityImageStorage communityImageStorage;

    /** 호출하면 목록 항목을 만든다. 첫 이미지의 조회 URL(thumbnailUrl)과 이미지 수를 담고, images는 비운다. */
    CommunityPostItem toListItem(CommunityPostRow row) {
        String thumbnailUrl = row.thumbnailKey() == null ? null : communityImageStorage.presignView(row.thumbnailKey());
        return CommunityPostItem.from(row, thumbnailUrl, List.of());
    }

    /**
     * 호출하면 ACTIVE인 글 한 건을 본문 전체와 이미지 목록과 함께 읽는다. 글이 없거나 ACTIVE가 아니면 빈 값이다.
     * viewerId가 null이면 likedByMe를 읽지 않는다.
     */
    Optional<CommunityPostItem> findDetail(long postId, Long viewerId) {
        return communityPostMapper.selectActiveById(postId, viewerId).map(row -> {
            List<CommunityPostImage> images = communityImageMapper.selectByPostId(postId).stream()
                    .map(image -> new CommunityPostImage(
                            image.imageId(), communityImageStorage.presignView(image.objectKey())))
                    .toList();
            return CommunityPostItem.from(row, null, images);
        });
    }
}
