package com.pitchmap.community.application;

import com.pitchmap.common.error.InvalidFieldException;
import com.pitchmap.community.domain.CommunityImage;
import com.pitchmap.community.domain.CommunityImageRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 글에 붙이려는 이미지가 붙일 수 있는 이미지인지 저장소까지 확인한다. 이 클래스는 트랜잭션을 열지 않는다.
 *
 * <p>저장소에 객체가 있는지 묻는 호출은 느릴 수 있어서, 글을 저장하는 트랜잭션이 DB 커넥션과 행 잠금을 쥔 채로 기다리면 안 된다. 그래서
 * 글을 저장하기 전에 트랜잭션 밖에서 확인한다. 하지만 확인한 뒤 저장하기 전에 다른 요청이 같은 이미지를 붙일 수 있으므로, 트랜잭션 안에서
 * {@link CommunityPostImageAttacher}가 행을 잠그고 같은 조건을 다시 검사한다. 여기서 한 검사는 빨리 거부하기 위한 것이다.
 */
@Component
@RequiredArgsConstructor
class CommunityImageAttachmentChecker {

    static final String FIELD = "imageIds";
    static final String UNUSABLE_MESSAGE = "붙일 수 없는 이미지가 있습니다.";

    private final CommunityImageRepository communityImageRepository;
    private final CommunityImageStorage communityImageStorage;

    /**
     * 호출하면 imageIds를 memberId인 회원이 postId인 글(새 글이면 null)에 붙일 수 있는지 확인한다.
     *
     * <p>5장을 넘거나 같은 ID가 겹치거나, 없는 이미지거나 다른 회원이 올린 이미지거나 다른 글에 이미 붙었거나, 저장소에 객체가 없거나
     * 올린 크기가 발급할 때 서명한 크기와 다르면 {@code imageIds} 필드의 INVALID_INPUT으로 거부한다. 이미 이 글에 붙은 이미지는
     * 저장소를 다시 확인하지 않는다.
     */
    void verify(long memberId, Long postId, List<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return;
        }
        requireValidShape(imageIds);
        Map<Long, CommunityImage> images = new HashMap<>();
        communityImageRepository.findAllById(imageIds).forEach(image -> images.put(image.getId(), image));
        for (Long imageId : imageIds) {
            CommunityImage image = images.get(imageId);
            requireAttachable(image, memberId, postId);
            if (!isAttachedTo(image, postId)) {
                requireUploaded(image);
            }
        }
    }

    private static void requireValidShape(List<Long> imageIds) {
        if (imageIds.size() > CommunityImage.MAX_PER_POST) {
            throw invalid("이미지는 " + CommunityImage.MAX_PER_POST + "장까지 붙일 수 있습니다.");
        }
        if (new HashSet<>(imageIds).size() != imageIds.size()) {
            throw invalid("같은 이미지를 두 번 붙일 수 없습니다.");
        }
        if (imageIds.stream().anyMatch(id -> id == null)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
    }

    // 없는 이미지와 남의 이미지를 같은 메시지로 거부해서, 다른 회원의 이미지 ID가 존재하는지 알 수 없게 한다.
    private static void requireAttachable(CommunityImage image, long memberId, Long postId) {
        if (image == null || !image.isUploadedBy(memberId)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
        if (image.isAttached() && !isAttachedTo(image, postId)) {
            throw invalid(UNUSABLE_MESSAGE);
        }
    }

    private static boolean isAttachedTo(CommunityImage image, Long postId) {
        return postId != null && image.isAttachedTo(postId);
    }

    private void requireUploaded(CommunityImage image) {
        boolean uploaded = communityImageStorage
                .findObjectSize(image.getObjectKey())
                .filter(size -> size == image.getSizeBytes())
                .isPresent();
        if (!uploaded) {
            throw invalid(UNUSABLE_MESSAGE);
        }
    }

    static InvalidFieldException invalid(String reason) {
        return new InvalidFieldException(FIELD, reason);
    }
}
