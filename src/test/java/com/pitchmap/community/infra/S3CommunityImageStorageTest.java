package com.pitchmap.community.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pitchmap.community.application.CommunityImageStorage.PresignedUpload;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3CommunityImageStorageTest {

    private static final String BUCKET = "pitchmap-upload-test";
    private static final String KEY = "community/7/3f0c1c7e-0000-4000-8000-000000000001.jpg";

    private S3Presigner presigner;
    private S3CommunityImageStorage storage;

    @BeforeEach
    void setUp() {
        // 서명 계산에만 쓰는 고정 자격 증명이다. 네트워크를 쓰지 않는다.
        presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access", "test-secret")))
                .build();
        CommunityImageProperties properties = new CommunityImageProperties(
                "s3",
                BUCKET,
                "ap-northeast-2",
                Duration.ofMinutes(10),
                Duration.ofHours(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
        storage = new S3CommunityImageStorage(properties, mock(S3Client.class), presigner);
    }

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    @DisplayName("[F-29][CM-06] 업로드 URL은 버킷의 서울 리전 주소이고 content-type과 content-length를 서명 헤더에 넣으며 체크섬 파라미터가 없다")
    void presignUploadSignsContentTypeAndLength() {
        // given
        Instant before = Instant.now();

        // when
        PresignedUpload upload = storage.presignUpload(KEY, "image/jpeg", 2483011L);

        // then
        String url = upload.url();
        assertThat(url).startsWith("https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/" + KEY + "?");
        assertThat(url).contains("X-Amz-Signature=");
        assertThat(url)
                .containsIgnoringCase("X-Amz-SignedHeaders=")
                .contains("content-type")
                .contains("content-length");
        assertThat(url.toLowerCase())
                .doesNotContain("x-amz-sdk-checksum-algorithm")
                .doesNotContain("x-amz-checksum");
        assertThat(upload.headers()).isEqualTo(Map.of("Content-Type", "image/jpeg", "Content-Length", "2483011"));
        assertThat(upload.expiresAt())
                .isBetween(
                        before.plus(Duration.ofMinutes(10)).minusSeconds(5),
                        Instant.now().plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("[F-29][CM-06] 조회 URL은 서명을 담고 객체 키를 경로로 가리킨다")
    void presignViewContainsSignature() {
        // when
        String url = storage.presignView(KEY);

        // then
        assertThat(url).startsWith("https://" + BUCKET + ".s3.ap-northeast-2.amazonaws.com/" + KEY + "?");
        assertThat(url).contains("X-Amz-Signature=").contains("X-Amz-Expires=3600");
    }
}
