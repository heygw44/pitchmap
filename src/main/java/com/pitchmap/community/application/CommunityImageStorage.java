package com.pitchmap.community.application;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 커뮤니티 이미지를 담는 객체 저장소. 서버는 파일을 직접 받지 않고, 회원이 저장소에 바로 올리고 읽을 수 있는 사전 서명 URL만 발급한다.
 * 구현체는 운영에서 S3를 쓰고, 로컬 개발에서는 파일을 저장하지 않는 가짜를 쓴다.
 */
public interface CommunityImageStorage {

    /**
     * 호출하면 key 위치에 contentType과 sizeBytes인 파일 하나를 올릴 수 있는 URL을 발급한다. 형식과 크기는 서명에 들어가므로 다르게 올리면 저장소가
     * 거부한다. 서버 안에서 서명을 계산하고 저장소를 부르지 않는다.
     */
    PresignedUpload presignUpload(String key, String contentType, long sizeBytes);

    /** 호출하면 key 위치에 올라온 파일의 크기(바이트)를 돌려준다. 파일이 없으면 빈 값이다. 저장소를 부르므로 트랜잭션 밖에서 쓴다. */
    Optional<Long> findObjectSize(String key);

    /** 호출하면 key 위치의 파일을 읽을 수 있는 URL을 발급한다. 서버 안에서 서명을 계산하고 저장소를 부르지 않는다. */
    String presignView(String key);

    /** 호출하면 key 위치의 파일을 지운다. 파일이 없어도 오류로 보지 않는다. 저장소를 부르므로 트랜잭션 밖에서 쓴다. */
    void delete(String key);

    /**
     * 업로드 URL과 그 URL로 보낼 때 함께 보낼 헤더.
     *
     * @param url 파일을 PUT으로 올릴 사전 서명 URL
     * @param headers 요청에 그대로 붙여야 하는 헤더(서명에 들어간 Content-Type과 Content-Length)
     * @param expiresAt URL을 더 쓸 수 없게 되는 시각
     */
    record PresignedUpload(String url, Map<String, String> headers, Instant expiresAt) {}
}
