package com.pitchmap.community.api;

import com.pitchmap.community.application.CommunityImageUpload;
import java.time.Instant;
import java.util.Map;

// 화면은 uploadUrl로 파일을 PUT하면서 headers를 그대로 보낸다. 저장소 객체 키는 내보내지 않는다.
public record CommunityImageUploadResponse(
        long imageId, String uploadUrl, String method, Map<String, String> headers, Instant expiresAt) {

    private static final String UPLOAD_METHOD = "PUT";

    static CommunityImageUploadResponse from(CommunityImageUpload upload) {
        return new CommunityImageUploadResponse(
                upload.imageId(), upload.uploadUrl(), UPLOAD_METHOD, upload.headers(), upload.expiresAt());
    }
}
