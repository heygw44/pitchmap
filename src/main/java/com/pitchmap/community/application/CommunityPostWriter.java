package com.pitchmap.community.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.web.PatchField;
import com.pitchmap.community.domain.CommunityPost;
import com.pitchmap.community.domain.CommunityPostRepository;
import com.pitchmap.spot.application.ActiveSpotChecker;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글을 저장하고 고치고 지우는 트랜잭션 단위. {@link CommunityPostCommandService}가 트랜잭션 밖에서 이미지 확인을 마친 뒤 여기를 부른다.
 *
 * <p>회원이 커뮤니티 글을 쓰고, 자기 글을 고치거나 지운다.
 *
 * <p>글에 연결할 수 있는 장소는 지도에 보이는(ACTIVE) 장소뿐이다. 장소가 없거나 ACTIVE가 아니면 서비스는 INVALID_INPUT으로 거부한다.
 * 고치거나 지울 때는 글이 없거나 ACTIVE가 아니면(삭제, 숨김, 검토 대기) NOT_FOUND로 거부한다. 작성자 본인에게도 같다. 글이 있는데
 * 작성자가 아니면 ACCESS_DENIED로 거부하므로, 검사 순서는 글 확인, 작성자 확인이다.
 *
 * <p>글을 지울 때는 상태만 DELETED로 바꾸고 행을 남긴다. 이후에 신고 기록과 감사 로그가 글을 가리키기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class CommunityPostWriter {

    private final ActiveSpotChecker activeSpotChecker;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostItemReader communityPostItemReader;
    private final CommunityPostImageAttacher communityPostImageAttacher;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 글을 저장하고 글 ID를 돌려준다.
     *
     * <p>제목이 공백뿐이거나 100자를 넘거나, 본문이 공백뿐이거나 10,000자를 넘거나, 연결할 장소가 ACTIVE가 아니면 INVALID_INPUT으로
     * 거부한다. 이미지는 잠근 뒤 소유자와 붙은 상태를 다시 검사하고, 어기면 글도 저장하지 않는다.
     */
    @Transactional
    public long write(long memberId, CommunityPostWriteCommand command) {
        requireValidTitle(command.title());
        requireValidContent(command.content());
        if (command.spotId() != null) {
            requireLinkable(command.spotId());
        }
        CommunityPost post = CommunityPost.write(
                memberId, command.category(), command.title(), command.content(), command.spotId(), clock.instant());
        long postId = communityPostRepository.saveAndFlush(post).getId();
        if (!command.imageIds().isEmpty()) {
            communityPostImageAttacher.replace(memberId, postId, command.imageIds());
        }
        return postId;
    }

    /**
     * 호출하면 작성자 memberId가 postId인 글에서 요청에 담은 필드만 고치고, 고친 뒤의 글을 돌려준다. 요청에 없는 필드는 그대로 둔다.
     *
     * <p>글이 없거나 ACTIVE가 아니면 NOT_FOUND로, 다른 회원이 쓴 글이면 ACCESS_DENIED로 거부한다. 카테고리, 제목, 본문을 null로 보냈거나
     * 값이 규칙을 어기거나, 연결할 장소가 ACTIVE가 아니면 INVALID_INPUT으로 거부한다.
     */
    @Transactional
    public CommunityPostItem revise(long memberId, long postId, CommunityPostReviseCommand command) {
        CommunityPost post = loadOwnActivePost(memberId, postId);
        Instant now = clock.instant();
        applyChanges(post, command, now);
        // imageIds를 보냈으면 서비스가 null을 이미 거부했으므로 값이 있다.
        if (command.imageIds().present()
                && communityPostImageAttacher.replace(
                        memberId, postId, command.imageIds().value())) {
            post.markEdited(now);
        }
        // 고친 내용을 MyBatis로 읽기 전에 DB에 반영한다.
        communityPostRepository.flush();
        return communityPostItemReader
                .findDetail(postId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /**
     * 호출하면 작성자 memberId의 글을 DELETED로 바꾼다. 행은 남는다.
     *
     * <p>글이 없거나 ACTIVE가 아니면 NOT_FOUND로, 다른 회원이 쓴 글이면 ACCESS_DENIED로 거부한다.
     */
    @Transactional
    public void delete(long memberId, long postId) {
        loadOwnActivePost(memberId, postId).delete(clock.instant());
    }

    /** 호출하면 postId인 글이 ACTIVE이고 memberId인 회원이 쓴 글인지만 확인한다. 아니면 {@link #revise}와 같은 예외로 거부한다. */
    @Transactional(readOnly = true)
    public void requireOwnActivePost(long memberId, long postId) {
        loadOwnActivePost(memberId, postId);
    }

    private CommunityPost loadOwnActivePost(long memberId, long postId) {
        CommunityPost post = communityPostRepository
                .findById(postId)
                .filter(CommunityPost::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!post.isWrittenBy(memberId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return post;
    }

    private void applyChanges(CommunityPost post, CommunityPostReviseCommand command, Instant now) {
        requireNotCleared(command.category(), "카테고리");
        requireNotCleared(command.title(), "제목");
        requireNotCleared(command.content(), "본문");
        if (command.title().present()) {
            requireValidTitle(command.title().value());
        }
        if (command.content().present()) {
            requireValidContent(command.content().value());
        }
        Long newSpotId = command.spotId().value();
        if (command.spotId().present() && newSpotId != null) {
            requireLinkable(newSpotId);
        }
        post.revise(
                command.category().value(),
                command.title().value(),
                command.content().value(),
                now);
        if (command.spotId().present()) {
            post.changeSpot(newSpotId, now);
        }
    }

    // 카테고리, 제목, 본문은 지울 수 있는 값이 아니다. 요청에 있는데 null이면 지우려는 것으로 보고 거부한다.
    private static void requireNotCleared(PatchField<?> field, String label) {
        if (field.present() && field.value() == null) {
            throw invalidInput(label + "은(는) 비울 수 없습니다.");
        }
    }

    private void requireLinkable(long spotId) {
        if (!activeSpotChecker.isActive(spotId)) {
            throw invalidInput("연결할 수 없는 장소입니다.");
        }
    }

    private static void requireValidTitle(String title) {
        if (title == null || title.isBlank()) {
            throw invalidInput("제목을 입력해야 합니다.");
        }
        if (title.length() > CommunityPost.TITLE_MAX_LENGTH) {
            throw invalidInput("제목은 " + CommunityPost.TITLE_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    private static void requireValidContent(String content) {
        if (content == null || content.isBlank()) {
            throw invalidInput("본문을 입력해야 합니다.");
        }
        if (content.length() > CommunityPost.CONTENT_MAX_LENGTH) {
            throw invalidInput("본문은 " + CommunityPost.CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
    }
}
