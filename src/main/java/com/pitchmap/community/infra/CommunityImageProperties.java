package com.pitchmap.community.infra;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 커뮤니티 이미지 저장소 설정.
 *
 * @param storage 저장소 종류. {@code fake}는 로컬 개발용으로 파일을 저장하지 않고, {@code s3}는 실제 S3 버킷을 쓴다.
 * @param bucket S3 버킷 이름. 비밀값이 아니다. storage가 s3이면 비어 있을 수 없다.
 * @param region 버킷이 있는 AWS 리전
 * @param uploadUrlTtl 업로드 URL을 쓸 수 있는 시간
 * @param viewUrlTtl 조회 URL을 쓸 수 있는 시간
 * @param apiCallAttemptTimeout S3 API 호출 한 번(재시도 한 번)을 기다리는 시간
 * @param apiCallTimeout 재시도를 포함해 S3 API 호출 하나가 끝나기를 기다리는 시간
 */
@ConfigurationProperties("pitchmap.community.image")
public record CommunityImageProperties(
        @DefaultValue("fake") String storage,
        String bucket,
        @DefaultValue("ap-northeast-2") String region,
        @DefaultValue("10m") Duration uploadUrlTtl,
        @DefaultValue("1h") Duration viewUrlTtl,
        @DefaultValue("2s") Duration apiCallAttemptTimeout,
        @DefaultValue("5s") Duration apiCallTimeout) {

    public static final String STORAGE_FAKE = "fake";
    public static final String STORAGE_S3 = "s3";

    private static final String PREFIX = "pitchmap.community.image.";

    public CommunityImageProperties {
        bucket = Objects.requireNonNullElse(bucket, "");
        if (!STORAGE_FAKE.equals(storage) && !STORAGE_S3.equals(storage)) {
            throw new IllegalArgumentException(PREFIX + "storage must be fake or s3");
        }
        if (STORAGE_S3.equals(storage) && bucket.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "bucket must not be blank when storage is s3");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException(PREFIX + "region must not be blank");
        }
        requirePositive(uploadUrlTtl, "uploadUrlTtl");
        requirePositive(viewUrlTtl, "viewUrlTtl");
        requirePositive(apiCallAttemptTimeout, "apiCallAttemptTimeout");
        requirePositive(apiCallTimeout, "apiCallTimeout");
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(PREFIX + name + " must be positive");
        }
    }
}
