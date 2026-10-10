package com.pitchmap.community.application;

import com.pitchmap.common.web.PatchField;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 회원이 커뮤니티 글을 쓰고, 자기 글을 고치거나 지운다.
 *
 * <p>이 서비스는 트랜잭션을 열지 않는다. 글에 이미지를 붙일 때 저장소에 객체가 있는지 묻는 호출은 DB 트랜잭션 밖에서 해야 하기 때문이다.
 * 이미지를 확인한 뒤에 트랜잭션을 가진 {@link CommunityPostWriter}를 불러 글을 저장한다. 글을 저장하는 규칙(장소, 제목, 본문, 작성자 검사)은
 * 그쪽에 있다.
 */
@Service
@RequiredArgsConstructor
public class CommunityPostCommandService {

    private final CommunityPostWriter communityPostWriter;
    private final CommunityImageAttachmentChecker communityImageAttachmentChecker;

    /**
     * 호출하면 memberId인 회원의 글을 저장하고 글 ID를 돌려준다.
     *
     * <p>제목이 공백뿐이거나 100자를 넘거나, 본문이 공백뿐이거나 10,000자를 넘거나, 연결할 장소가 ACTIVE가 아니면 INVALID_INPUT으로
     * 거부한다. 붙일 이미지가 5장을 넘거나 겹치거나, 회원이 올리지 않았거나, 이미 다른 글에 붙었거나, 저장소에 없거나 크기가 다르면 {@code imageIds}
     * 필드의 INVALID_INPUT으로 거부한다.
     */
    public long write(long memberId, CommunityPostWriteCommand command) {
        communityImageAttachmentChecker.verify(memberId, null, command.imageIds());
        return communityPostWriter.write(memberId, command);
    }

    /**
     * 호출하면 작성자 memberId가 postId인 글에서 요청에 담은 필드만 고치고, 고친 뒤의 글을 돌려준다. 요청에 없는 필드는 그대로 둔다.
     *
     * <p>글이 없거나 ACTIVE가 아니면 NOT_FOUND로, 다른 회원이 쓴 글이면 ACCESS_DENIED로 거부한다. 카테고리, 제목, 본문, imageIds를
     * null로 보냈거나 값이 규칙을 어기거나, 연결할 장소가 ACTIVE가 아니면 INVALID_INPUT으로 거부한다. imageIds를 보내면 글의 이미지를
     * 그 순서대로 통째로 바꾸고, 이미 이 글에 붙은 이미지는 다시 보내도 된다.
     */
    public CommunityPostItem revise(long memberId, long postId, CommunityPostReviseCommand command) {
        PatchField<List<Long>> imageIds = command.imageIds();
        if (imageIds.present()) {
            // 글이 없거나 남의 글이면 이미지 검사보다 먼저 404나 403으로 거부한다.
            communityPostWriter.requireOwnActivePost(memberId, postId);
            if (imageIds.value() == null) {
                throw CommunityImageAttachmentChecker.invalid("imageIds는 비울 수 없습니다. 이미지를 모두 떼려면 빈 배열을 보내야 합니다.");
            }
            communityImageAttachmentChecker.verify(memberId, postId, imageIds.value());
        }
        return communityPostWriter.revise(memberId, postId, command);
    }

    /**
     * 호출하면 작성자 memberId의 글을 DELETED로 바꾼다. 행은 남는다. 붙은 이미지도 글에 붙은 채로 둔다.
     *
     * <p>글이 없거나 ACTIVE가 아니면 NOT_FOUND로, 다른 회원이 쓴 글이면 ACCESS_DENIED로 거부한다.
     */
    public void delete(long memberId, long postId) {
        communityPostWriter.delete(memberId, postId);
    }
}
