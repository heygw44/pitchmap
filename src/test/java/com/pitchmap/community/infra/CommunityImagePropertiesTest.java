package com.pitchmap.community.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommunityImagePropertiesTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    private static CommunityImageProperties properties(String storage, String bucket, Duration uploadTtl) {
        return new CommunityImageProperties(
                storage,
                bucket,
                "ap-northeast-2",
                uploadTtl,
                Duration.ofHours(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("[F-29][CM-06] 저장소가 fake이면 버킷이 비어 있어도 앱이 시작한다")
    void fakeStorageDoesNotNeedBucket() {
        assertThat(properties("fake", null, TEN_MINUTES).bucket()).isEmpty();
    }

    @Test
    @DisplayName("[F-29][CM-06] 저장소가 s3인데 버킷이 비었으면 설정 이름을 담은 예외로 시작을 막는다")
    void s3WithoutBucketFails() {
        assertThatThrownBy(() -> properties("s3", " ", TEN_MINUTES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pitchmap.community.image.bucket");
    }

    @Test
    @DisplayName("[F-29][CM-06] 저장소 값이 fake나 s3가 아니면 예외로 시작을 막는다")
    void unknownStorageFails() {
        assertThatThrownBy(() -> properties("gcs", "bucket", TEN_MINUTES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pitchmap.community.image.storage");
    }

    @Test
    @DisplayName("[F-29][CM-06] 제한 시간이 0이거나 음수이면 예외로 시작을 막는다")
    void nonPositiveDurationFails() {
        assertThatThrownBy(() -> properties("s3", "bucket", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uploadUrlTtl");
        assertThatThrownBy(() -> properties("s3", "bucket", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
