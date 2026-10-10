package com.pitchmap.community.infra;

import com.pitchmap.community.application.CommunityImageStorage;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 버킷에 커뮤니티 이미지를 두는 저장소. 자격 증명은 SDK의 기본 방식으로 찾는다. 운영 서버에서는 인스턴스 역할이다.
 *
 * <p>사전 서명 URL은 서버 안에서 계산하므로 S3를 부르지 않는다. 객체 크기 확인과 삭제만 S3를 부르며, SDK는 기본으로 제한 시간이 없어서
 * 호출마다 제한 시간을 걸고 재시도는 한 번만 한다. 로그에는 키나 URL을 남기지 않는다. 키에 회원 ID가 들어 있고, URL에는 서명이 들어 있기 때문이다.
 */
@Component
@ConditionalOnProperty(
        prefix = "pitchmap.community.image",
        name = "storage",
        havingValue = CommunityImageProperties.STORAGE_S3)
public class S3CommunityImageStorage implements CommunityImageStorage, DisposableBean {

    private static final int MAX_ATTEMPTS = 2;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_LENGTH = "Content-Length";

    private final CommunityImageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Autowired
    public S3CommunityImageStorage(CommunityImageProperties properties) {
        this(properties, buildClient(properties), buildPresigner(properties));
    }

    // 테스트가 고정 자격 증명으로 만든 서명기를 넣을 수 있게 열어 둔다.
    S3CommunityImageStorage(CommunityImageProperties properties, S3Client s3Client, S3Presigner presigner) {
        this.properties = properties;
        this.s3Client = s3Client;
        this.presigner = presigner;
    }

    private static S3Client buildClient(CommunityImageProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(override -> override.apiCallTimeout(properties.apiCallTimeout())
                        .apiCallAttemptTimeout(properties.apiCallAttemptTimeout())
                        .retryStrategy(retry -> retry.maxAttempts(MAX_ATTEMPTS)))
                .build();
    }

    private static S3Presigner buildPresigner(CommunityImageProperties properties) {
        return S3Presigner.builder().region(Region.of(properties.region())).build();
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
        PutObjectRequest object = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(object)
                .build());
        return new PresignedUpload(
                presigned.url().toString(),
                Map.of(CONTENT_TYPE, contentType, CONTENT_LENGTH, String.valueOf(sizeBytes)),
                presigned.expiration());
    }

    @Override
    public Optional<Long> findObjectSize(String key) {
        try {
            return Optional.of(s3Client.headObject(HeadObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .build())
                    .contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public String presignView(String key) {
        Duration ttl = properties.viewUrlTtl();
        return presigner
                .presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(properties.bucket())
                                .key(key)
                                .build())
                        .build())
                .url()
                .toString();
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }

    @Override
    public void destroy() {
        s3Client.close();
        presigner.close();
    }
}
