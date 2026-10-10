package com.pitchmap.community.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.community.domain.CommunityCategory;
import com.pitchmap.community.infra.TestCommunityImageStorage;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CommunityImageCleanupIntegrationTest {

    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    // DATETIME 컬럼에 UTC 시각을 문자열로 넣는다. 드라이버의 시간대 변환이 끼어들지 않게 하려는 것이다.
    private static final DateTimeFormatter DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    @Autowired
    private CommunityImageCleanupService cleanupService;

    @Autowired
    private CommunityPostCommandService postCommandService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TestCommunityImageStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private final Map<Long, String> keys = new HashMap<>();
    private long memberId;
    private int sequence;

    @BeforeEach
    void setUp() {
        memberId = memberRepository.saveAndFlush(aMember().build()).getId();
    }

    @Test
    @DisplayName("[F-29][CM-06] 글에 붙지 않은 채 24시간보다 1초 더 지난 이미지는 행과 저장소 객체를 지우고, 정확히 24시간 된 이미지와 새 이미지는 남긴다")
    void deletesOnlyUnattachedImagesOlderThanTwentyFourHours() {
        // given
        Instant cutoff = clock.instant().minus(CommunityImageCleanupService.RETENTION);
        long older = insertImage(cutoff.minus(ONE_SECOND));
        long exact = insertImage(cutoff);
        long newer = insertImage(cutoff.plus(ONE_SECOND));

        // when
        CommunityImageCleanupService.Result result = cleanupService.deleteUnattached();

        // then
        assertThat(result.deletedRows()).isEqualTo(1);
        assertThat(result.storageFailures()).isZero();
        assertThat(exists(older)).isFalse();
        assertThat(exists(exact)).isTrue();
        assertThat(exists(newer)).isTrue();
        assertThat(storage.deletedKeys()).containsExactly(keyOf(older));
    }

    @Test
    @DisplayName("[F-29][CM-06] 오래됐어도 글에 붙은 이미지는 행도 저장소 객체도 지우지 않는다")
    void keepsAttachedImages() {
        // given
        long postId = postCommandService.write(
                memberId, new CommunityPostWriteCommand(CommunityCategory.FREE, "제목", "본문", null));
        Instant old = clock.instant().minus(Duration.ofDays(3));
        long attached = insertImage(old);
        long unattached = insertImage(old);
        jdbc.update("UPDATE community_image SET post_id = ?, display_order = 0 WHERE id = ?", postId, attached);

        // when
        CommunityImageCleanupService.Result result = cleanupService.deleteUnattached();

        // then
        assertThat(result.deletedRows()).isEqualTo(1);
        assertThat(exists(attached)).isTrue();
        assertThat(exists(unattached)).isFalse();
        assertThat(storage.deletedKeys()).doesNotContain(keyOf(attached));
    }

    @Test
    @DisplayName("[F-29][CM-06] 시계가 움직여 24시간을 넘기면 그때부터 지운다")
    void becomesDeletableWhenClockPassesRetention() {
        // given
        long image = insertImage(clock.instant());

        // when
        int before = cleanupService.deleteUnattached().deletedRows();
        clock.advance(CommunityImageCleanupService.RETENTION);
        int atBoundary = cleanupService.deleteUnattached().deletedRows();
        clock.advance(ONE_SECOND);
        int after = cleanupService.deleteUnattached().deletedRows();

        // then
        assertThat(before).isZero();
        assertThat(atBoundary).isZero();
        assertThat(after).isEqualTo(1);
        assertThat(exists(image)).isFalse();
    }

    @Test
    @DisplayName("[F-29][CM-06] 저장소 객체 삭제가 한 이미지에서 실패해도 나머지를 계속 지우고, 실패한 이미지의 행은 이미 지워진 채 실패 수에 센다")
    void storageDeleteFailureDoesNotStopCleanup() {
        // given
        Instant old = clock.instant().minus(Duration.ofDays(2));
        long failing = insertImage(old);
        long other = insertImage(old);
        storage.failDeleteOf(keyOf(failing));

        // when
        CommunityImageCleanupService.Result result = cleanupService.deleteUnattached();

        // then
        assertThat(result.deletedRows()).isEqualTo(2);
        assertThat(result.storageFailures()).isEqualTo(1);
        assertThat(exists(failing)).isFalse();
        assertThat(exists(other)).isFalse();
        assertThat(storage.deletedKeys()).containsExactly(keyOf(other));
    }

    @Test
    @DisplayName("[F-29][CM-06] 한 번에 읽는 상한보다 대상이 많아도 모두 지운다")
    void deletesBeyondBatchSize() {
        // given
        Instant old = clock.instant().minus(Duration.ofDays(2));
        int total = CommunityImageCleanupService.BATCH_SIZE + 1;
        for (int i = 0; i < total; i++) {
            insertImage(old);
        }

        // when
        CommunityImageCleanupService.Result result = cleanupService.deleteUnattached();

        // then
        assertThat(result.deletedRows()).isEqualTo(total);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM community_image", Integer.class))
                .isZero();
    }

    private long insertImage(Instant createdAt) {
        String key = "community/" + memberId + "/cleanup-" + (sequence++) + ".jpg";
        jdbc.update(
                "INSERT INTO community_image (member_id, post_id, object_key, content_type, size_bytes, display_order, created_at)"
                        + " VALUES (?, NULL, ?, 'image/jpeg', 1000, NULL, ?)",
                memberId,
                key,
                DATETIME_UTC.format(createdAt));
        long imageId = jdbc.queryForObject("SELECT MAX(id) FROM community_image", Long.class);
        keys.put(imageId, key);
        return imageId;
    }

    // 지워진 이미지의 키도 확인해야 해서 행을 만들 때 기록해 둔다.
    private String keyOf(long imageId) {
        return keys.get(imageId);
    }

    private boolean exists(long imageId) {
        Integer found =
                jdbc.queryForObject("SELECT COUNT(*) FROM community_image WHERE id = ?", Integer.class, imageId);
        return found != null && found == 1;
    }
}
