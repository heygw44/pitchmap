package com.pitchmap.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 커뮤니티 글에 붙이는 이미지 한 장. 서버는 파일을 받지 않고 저장소에 올릴 URL만 발급하므로, 이 객체는 저장소에 있는 파일의 이름표다.
 *
 * <p>업로드 URL을 발급할 때 글에 붙지 않은({@code postId}가 null) 상태로 만든다. 글에 붙이면 글 ID와 글 안 순서를 채우고, 글에서 빼면 다시
 * 붙지 않은 상태로 돌아간다. 붙지 않은 채 오래된 이미지는 정리 작업이 지운다. 올린 회원과 글은 다른 엔티티이므로 ID로만 가리킨다.
 */
@Entity
@Table(name = "community_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityImage {

    /** 이미지 한 장의 최대 크기(바이트)다. 5MB. */
    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    /** 글 하나에 붙일 수 있는 이미지의 최대 장수다. */
    public static final int MAX_PER_POST = 5;

    // 허용하는 형식과 저장소 객체 키에 붙일 확장자다.
    private static final Map<String, String> EXTENSIONS =
            Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private long memberId;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private int sizeBytes;

    // 컬럼이 TINYINT라서 Hibernate의 스키마 검증을 통과하려면 JDBC 타입을 맞춰야 한다.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at")
    private Instant createdAt;

    private CommunityImage(long memberId, String objectKey, String contentType, int sizeBytes, Instant now) {
        this.memberId = memberId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = now;
    }

    /**
     * 호출하면 memberId인 회원이 올릴 이미지를 글에 붙지 않은 상태로 만든다.
     *
     * <p>objectKey가 비었거나, 형식이 허용 목록에 없거나, 크기가 1바이트 미만이거나 {@value #MAX_SIZE_BYTES}바이트를 넘거나, now가 null이면
     * {@link IllegalArgumentException}을 던진다.
     */
    public static CommunityImage issue(
            long memberId, String objectKey, String contentType, long sizeBytes, Instant now) {
        if (objectKey == null || objectKey.isBlank() || now == null) {
            throw new IllegalArgumentException("이미지를 만드는 데 필요한 값이 비었습니다.");
        }
        if (!isAllowedContentType(contentType)) {
            throw new IllegalArgumentException("허용하지 않는 이미지 형식입니다.");
        }
        if (!isAllowedSize(sizeBytes)) {
            throw new IllegalArgumentException("이미지 크기는 1바이트 이상 " + MAX_SIZE_BYTES + "바이트 이하여야 합니다.");
        }
        return new CommunityImage(memberId, objectKey, contentType, (int) sizeBytes, now);
    }

    /** contentType이 허용하는 이미지 형식(JPEG, PNG, WebP)이면 true다. */
    public static boolean isAllowedContentType(String contentType) {
        return contentType != null && EXTENSIONS.containsKey(contentType);
    }

    /** sizeBytes가 1바이트 이상이고 {@value #MAX_SIZE_BYTES}바이트 이하이면 true다. */
    public static boolean isAllowedSize(long sizeBytes) {
        return sizeBytes >= 1 && sizeBytes <= MAX_SIZE_BYTES;
    }

    /** 호출하면 허용하는 형식의 객체 키 확장자를 돌려준다. 허용하지 않는 형식이면 빈 값이다. */
    public static Optional<String> extensionOf(String contentType) {
        return Optional.ofNullable(contentType).map(EXTENSIONS::get);
    }

    /** 호출하면 이미지를 글에 붙이고 글 안 순서를 order로 한다. order가 0 미만이거나 {@value #MAX_PER_POST} 이상이면 {@link IllegalArgumentException}을 던진다. */
    public void attachTo(long postId, int order) {
        if (order < 0 || order >= MAX_PER_POST) {
            throw new IllegalArgumentException("이미지 순서는 0 이상 " + MAX_PER_POST + " 미만이어야 합니다.");
        }
        this.postId = postId;
        this.displayOrder = order;
    }

    /** 호출하면 이미지를 글에서 떼어 붙지 않은 상태로 되돌린다. */
    public void detach() {
        this.postId = null;
        this.displayOrder = null;
    }

    /** memberId인 회원이 올린 이미지이면 true다. */
    public boolean isUploadedBy(long memberId) {
        return this.memberId == memberId;
    }

    /** postId인 글에 붙어 있으면 true다. */
    public boolean isAttachedTo(long postId) {
        return this.postId != null && this.postId == postId;
    }

    /** 어떤 글에든 붙어 있으면 true다. */
    public boolean isAttached() {
        return postId != null;
    }
}
