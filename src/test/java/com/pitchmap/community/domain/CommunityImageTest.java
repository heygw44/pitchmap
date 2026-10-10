package com.pitchmap.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommunityImageTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    private static CommunityImage issued() {
        return CommunityImage.issue(7L, "community/7/key.jpg", "image/jpeg", 1000L, NOW);
    }

    @Test
    @DisplayName("[F-29][CM-06] 이미지를 발급하면 글에 붙지 않은 상태이고 올린 회원만 올린 이미지로 본다")
    void issuedImageIsUnattached() {
        // when
        CommunityImage image = issued();

        // then
        assertThat(image.isAttached()).isFalse();
        assertThat(image.getDisplayOrder()).isNull();
        assertThat(image.isUploadedBy(7L)).isTrue();
        assertThat(image.isUploadedBy(8L)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"image/jpeg,jpg", "image/png,png", "image/webp,webp"})
    @DisplayName("[F-29][CM-06] JPEG, PNG, WebP를 허용하고 형식마다 확장자를 정한다")
    void allowedContentTypesHaveExtensions(String contentType, String extension) {
        assertThat(CommunityImage.isAllowedContentType(contentType)).isTrue();
        assertThat(CommunityImage.extensionOf(contentType)).contains(extension);
    }

    @Test
    @DisplayName("[F-29][CM-06] GIF와 null은 허용하지 않는다")
    void otherContentTypesAreRejected() {
        assertThat(CommunityImage.isAllowedContentType("image/gif")).isFalse();
        assertThat(CommunityImage.isAllowedContentType(null)).isFalse();
        assertThat(CommunityImage.extensionOf("image/gif")).isEmpty();
        assertThatThrownBy(() -> CommunityImage.issue(7L, "k", "image/gif", 1000L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29][CM-06] 크기는 1바이트부터 5MB(5242880바이트)까지 허용한다")
    void sizeBoundaries() {
        assertThat(CommunityImage.isAllowedSize(1)).isTrue();
        assertThat(CommunityImage.isAllowedSize(5_242_880L)).isTrue();
        assertThat(CommunityImage.isAllowedSize(0)).isFalse();
        assertThat(CommunityImage.isAllowedSize(5_242_881L)).isFalse();
        assertThatThrownBy(() -> CommunityImage.issue(7L, "k", "image/png", 5_242_881L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[F-29][CM-06] 글에 붙이면 글과 순서를 기록하고, 떼면 붙지 않은 상태로 돌아간다")
    void attachAndDetach() {
        // given
        CommunityImage image = issued();

        // when
        image.attachTo(9L, 2);

        // then
        assertThat(image.isAttached()).isTrue();
        assertThat(image.isAttachedTo(9L)).isTrue();
        assertThat(image.isAttachedTo(10L)).isFalse();
        assertThat(image.getDisplayOrder()).isEqualTo(2);

        // when
        image.detach();

        // then
        assertThat(image.isAttached()).isFalse();
        assertThat(image.getPostId()).isNull();
        assertThat(image.getDisplayOrder()).isNull();
    }

    @Test
    @DisplayName("[F-29][CM-06] 글 안 순서는 0부터 4까지만 허용한다")
    void orderRange() {
        CommunityImage image = issued();
        assertThatThrownBy(() -> image.attachTo(9L, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> image.attachTo(9L, 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
