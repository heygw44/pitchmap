package com.pitchmap.community.application;

import com.pitchmap.community.domain.CommunityImage;
import com.pitchmap.community.domain.CommunityImageRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글에 붙는 이미지 목록을 바꾼다. 글을 저장하는 트랜잭션 안에서만 부른다.
 *
 * <p>같은 이미지를 동시에 두 글에 붙이는 요청이 모두 통과하지 않도록 이미지 행을 쓰기 잠금으로 읽고, 잠금을 쥔 채 소유자와 붙은 상태를
 * 다시 검사한다. 늦게 온 요청은 잠금이 풀리기를 기다린 뒤 이미 붙은 상태를 보고 거부된다. 거부되면 예외가 트랜잭션 전체를 되돌려서 글도 저장되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CommunityPostImageAttacher {

    private final CommunityImageRepository communityImageRepository;

    /**
     * 호출하면 postId인 글의 이미지를 imageIds 순서대로 통째로 바꾸고, 목록이 바뀌었으면 true를 돌려준다. 목록에서 빠진 이미지는 글에서
     * 뗀다. 빈 목록이면 붙은 이미지를 모두 뗀다.
     *
     * <p>이미지가 없거나, 다른 회원이 올렸거나, 다른 글에 붙어 있으면 {@code imageIds} 필드의 INVALID_INPUT으로 거부한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replace(long memberId, long postId, List<Long> imageIds) {
        List<Long> currentIds = communityImageRepository.findIdsByPostId(postId);
        if (currentIds.isEmpty() && imageIds.isEmpty()) {
            return false;
        }
        Set<Long> lockIds = new LinkedHashSet<>(imageIds);
        lockIds.addAll(currentIds);
        Map<Long, CommunityImage> locked = lock(lockIds);
        List<CommunityImage> next = new ArrayList<>();
        for (Long imageId : imageIds) {
            next.add(requireAttachable(locked.get(imageId), memberId, postId));
        }
        for (Long currentId : currentIds) {
            if (!imageIds.contains(currentId)) {
                locked.get(currentId).detach();
            }
        }
        for (int order = 0; order < next.size(); order++) {
            next.get(order).attachTo(postId, order);
        }
        return !currentIds.equals(imageIds);
    }

    private Map<Long, CommunityImage> lock(Collection<Long> ids) {
        Map<Long, CommunityImage> locked = new HashMap<>();
        communityImageRepository.findAllByIdForUpdate(ids).forEach(image -> locked.put(image.getId(), image));
        return locked;
    }

    private static CommunityImage requireAttachable(CommunityImage image, long memberId, long postId) {
        boolean attachable =
                image != null && image.isUploadedBy(memberId) && (!image.isAttached() || image.isAttachedTo(postId));
        if (!attachable) {
            throw CommunityImageAttachmentChecker.invalid(CommunityImageAttachmentChecker.UNUSABLE_MESSAGE);
        }
        return image;
    }
}
