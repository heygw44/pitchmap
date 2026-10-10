package com.pitchmap.community.application;

import java.time.Instant;
import java.util.Map;

/**
 * 발급한 이미지 업로드 URL. 회원은 uploadUrl로 파일을 PUT하면서 headers를 그대로 보낸다.
 *
 * @param imageId 글을 쓰거나 고칠 때 imageIds에 넣을 이미지 ID
 * @param headers 서명에 들어간 Content-Type과 Content-Length
 * @param expiresAt uploadUrl을 더 쓸 수 없게 되는 시각
 */
public record CommunityImageUpload(long imageId, String uploadUrl, Map<String, String> headers, Instant expiresAt) {}
