package com.pitchmap.community.infra;

import com.pitchmap.community.application.CommunityImageStorage;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AWS 없이 로컬에서 개발할 때만 쓰는 가짜 저장소다. 파일을 저장하지 않고, 업로드 URL과 조회 URL도 실제로는 열리지 않는 주소다.
 * 발급한 업로드 URL의 키와 크기만 메모리에 기억해서, 글에 이미지를 붙이는 흐름이 로컬에서도 끝까지 돌게 한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "pitchmap.community.image",
        name = "storage",
        havingValue = CommunityImageProperties.STORAGE_FAKE,
        matchIfMissing = true)
public class LocalFakeCommunityImageStorage implements CommunityImageStorage {

    private static final String UPLOAD_URL_PREFIX = "http://localhost/local-fake-upload/";
    private static final String VIEW_URL_PREFIX = "http://localhost/local-fake-view/";

    private final CommunityImageProperties properties;
    private final Clock clock;
    private final Map<String, Long> sizes = new ConcurrentHashMap<>();

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes) {
        sizes.put(key, sizeBytes);
        return new PresignedUpload(
                UPLOAD_URL_PREFIX + key,
                Map.of("Content-Type", contentType, "Content-Length", String.valueOf(sizeBytes)),
                clock.instant().plus(properties.uploadUrlTtl()));
    }

    @Override
    public Optional<Long> findObjectSize(String key) {
        return Optional.ofNullable(sizes.get(key));
    }

    @Override
    public String presignView(String key) {
        return VIEW_URL_PREFIX + key;
    }

    @Override
    public void delete(String key) {
        sizes.remove(key);
    }
}
