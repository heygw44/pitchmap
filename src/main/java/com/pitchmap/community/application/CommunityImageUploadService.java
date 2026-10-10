package com.pitchmap.community.application;

import com.pitchmap.common.error.InvalidFieldException;
import com.pitchmap.community.application.CommunityImageStorage.PresignedUpload;
import com.pitchmap.community.domain.CommunityImage;
import com.pitchmap.community.domain.CommunityImageRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 글에 붙일 이미지를 올릴 URL을 발급한다. 서버는 파일을 받지 않는다.
 *
 * <p>URL을 발급할 때 글에 붙지 않은 이미지 행을 먼저 만든다. 회원이 올리지 않거나 글에 붙이지 않은 이미지는 정리 작업이 24시간 뒤에 지운다.
 * 저장소 키는 서버가 {@code community/{회원 ID}/{UUID}.{확장자}}로 만든다. 사전 서명은 서버 안에서 계산하므로 저장소를 부르지 않고, 트랜잭션 안에서 해도 된다.
 */
@Service
@RequiredArgsConstructor
public class CommunityImageUploadService {

    private final CommunityImageRepository communityImageRepository;
    private final CommunityImageStorage communityImageStorage;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원이 contentType인 sizeBytes 크기의 파일을 올릴 URL을 발급하고 이미지 행을 만든다.
     *
     * <p>허용하지 않는 형식이거나 크기가 1바이트 미만이거나 5MB를 넘으면 INVALID_INPUT으로 거부한다.
     */
    @Transactional
    public CommunityImageUpload issue(long memberId, String contentType, long sizeBytes) {
        String extension = CommunityImage.extensionOf(contentType)
                .orElseThrow(() -> new InvalidFieldException("contentType", "허용하지 않는 이미지 형식입니다."));
        if (!CommunityImage.isAllowedSize(sizeBytes)) {
            throw new InvalidFieldException("sizeBytes", "이미지 크기는 1바이트 이상 5MB 이하여야 합니다.");
        }
        String key = "community/" + memberId + "/" + UUID.randomUUID() + "." + extension;
        CommunityImage image = communityImageRepository.saveAndFlush(
                CommunityImage.issue(memberId, key, contentType, sizeBytes, clock.instant()));
        PresignedUpload upload = communityImageStorage.presignUpload(key, contentType, sizeBytes);
        return new CommunityImageUpload(image.getId(), upload.url(), upload.headers(), upload.expiresAt());
    }
}
