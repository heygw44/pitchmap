package com.pitchmap.community.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.community.api.CommunityApiFixture.POSTS;
import static com.pitchmap.community.api.CommunityApiFixture.postIdOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.community.infra.TestCommunityImageStorage;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class CommunityImageApiIntegrationTest {

    private static final String IMAGES = "/api/community/images";
    private static final long SIZE = 2483011L;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestCommunityImageStorage storage;

    private CommunityApiFixture fixture;
    private Member author;
    private Cookie session;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
        author = fixture.saveMember("새벽능선");
        session = fixture.verifiedSession(author);
    }

    @Test
    @DisplayName("[F-29][CM-06] 인증 회원이 업로드 URL을 받으면 201과 PUT 방식, 서명 헤더를 응답하고 글에 붙지 않은 이미지 행을 만든다")
    void issueUploadUrl() {
        // when
        MvcTestResult result = fixture.send(
                mvc.post().uri(IMAGES), session, "{\"contentType\":\"image/png\",\"sizeBytes\":" + SIZE + "}");

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        long imageId = idOf(result);
        assertThat(result).bodyJson().extractingPath("$.method").isEqualTo("PUT");
        assertThat(result).bodyJson().extractingPath("$.headers.Content-Type").isEqualTo("image/png");
        assertThat(result).bodyJson().extractingPath("$.headers.Content-Length").isEqualTo(String.valueOf(SIZE));
        assertThat(result).bodyJson().extractingPath("$.uploadUrl").asString().contains(keyOf(imageId));
        assertThat(keyOf(imageId)).matches("community/" + author.getId() + "/[0-9a-f-]{36}\\.png");
        assertThat(postIdOfImage(imageId)).isNull();
        assertThat(jdbc.queryForObject("SELECT member_id FROM community_image WHERE id = ?", Long.class, imageId))
                .isEqualTo(author.getId());
    }

    @Test
    @DisplayName("[F-29][CM-06] 로그인하지 않으면 401, 이메일 인증 전이면 403, 허용하지 않는 형식이면 400이고 이미지 행이 생기지 않는다")
    void issueUploadUrlRequiresVerifiedMemberAndValidBody() {
        // given
        Cookie unverified = fixture.loginOnly(fixture.saveMember("미인증"));
        String body = "{\"contentType\":\"image/jpeg\",\"sizeBytes\":1000}";

        // when
        MvcTestResult anonymous = mvc.post()
                .uri(IMAGES)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        MvcTestResult notVerified = fixture.send(mvc.post().uri(IMAGES), unverified, body);
        MvcTestResult gif =
                fixture.send(mvc.post().uri(IMAGES), session, "{\"contentType\":\"image/gif\",\"sizeBytes\":1000}");

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(notVerified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(gif).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(imageRowCount()).isZero();
    }

    @Test
    @DisplayName("[F-29][CM-06] 이미지 2장을 붙여 글을 쓰면 상세에 글 안 순서대로 URL이 나오고, 목록에는 첫 이미지 thumbnailUrl과 imageCount가 나온다")
    void createPostWithImages() {
        // given
        long first = uploadedImage(session, SIZE);
        long second = uploadedImage(session, 1000L);

        // when
        MvcTestResult created = writePost(session, List.of(second, first));
        long postId = postIdOf(created);
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();
        MvcTestResult list = mvc.get().uri(POSTS).exchange();

        // then
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail).bodyJson().extractingPath("$.images").asList().hasSize(2);
        assertThat(detail).bodyJson().extractingPath("$.images[0].imageId").isEqualTo((int) second);
        assertThat(detail).bodyJson().extractingPath("$.images[1].imageId").isEqualTo((int) first);
        assertThat(detail)
                .bodyJson()
                .extractingPath("$.images[0].url")
                .asString()
                .startsWith("https://view.test.invalid/" + keyOf(second));
        assertThat(detail).bodyJson().doesNotHavePath("$.thumbnailUrl");
        assertThat(list).bodyJson().extractingPath("$.content[0].imageCount").isEqualTo(2);
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0].thumbnailUrl")
                .asString()
                .startsWith("https://view.test.invalid/" + keyOf(second));
        assertThat(postIdOfImage(first)).isEqualTo(postId);
        assertThat(orderOfImage(second)).isZero();
        assertThat(orderOfImage(first)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-29][CM-06] 이미지가 없는 글의 목록 항목은 imageCount가 0이고 thumbnailUrl 필드가 없으며, 비로그인 목록 조회도 된다")
    void postWithoutImagesHasNoThumbnail() {
        // given
        fixture.writePost(session, "FREE", "제목", "본문", null);

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();

        // then
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list).bodyJson().extractingPath("$.content[0].imageCount").isEqualTo(0);
        assertThat(list).bodyJson().doesNotHavePath("$.content[0].thumbnailUrl");
    }

    @Test
    @DisplayName(
            "[F-29][CM-06] 저장소에 올라오지 않았거나 올린 크기가 서명한 크기와 다른 이미지를 붙이면 400 INVALID_INPUT과 imageIds 필드 오류이고 글도 저장하지 않는다")
    void rejectsNotUploadedOrDifferentSize() {
        // given
        long notUploaded = issueImage(session, SIZE);
        long wrongSize = issueImage(session, SIZE);
        storage.markUploaded(keyOf(wrongSize), SIZE + 1);

        // when
        MvcTestResult missing = writePost(session, List.of(notUploaded));
        MvcTestResult different = writePost(session, List.of(wrongSize));

        // then
        assertImageIdsRejected(missing);
        assertImageIdsRejected(different);
        assertThat(fixture.postCount()).isZero();
    }

    @Test
    @DisplayName("[F-29][CM-06] 이미지 6장을 붙이거나 같은 ID를 겹쳐 보내면 400 INVALID_INPUT과 imageIds 필드 오류다")
    void rejectsTooManyAndDuplicates() {
        // given
        List<Long> six = LongStream.range(0, 6)
                .mapToObj(i -> uploadedImage(session, 1000L))
                .collect(Collectors.toList());
        long one = six.get(0);

        // when
        MvcTestResult tooMany = writePost(session, six);
        MvcTestResult duplicated = writePost(session, List.of(one, one));

        // then
        assertImageIdsRejected(tooMany);
        assertImageIdsRejected(duplicated);
        assertThat(fixture.postCount()).isZero();
    }

    @Test
    @DisplayName("[F-29][CM-06] 5장은 붙일 수 있다")
    void acceptsFiveImages() {
        // given
        List<Long> five = LongStream.range(0, 5)
                .mapToObj(i -> uploadedImage(session, 1000L))
                .collect(Collectors.toList());

        // when
        MvcTestResult created = writePost(session, five);

        // then
        assertThat(created).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[F-29][CM-06] 다른 회원이 올린 이미지나 없는 이미지를 붙이면 400 INVALID_INPUT이고 그 이미지는 글에 붙지 않는다")
    void rejectsOthersAndMissingImages() {
        // given
        Cookie other = fixture.verifiedSession(fixture.saveMember("다른회원"));
        long othersImage = uploadedImage(other, SIZE);

        // when
        MvcTestResult stolen = writePost(session, List.of(othersImage));
        MvcTestResult missing = writePost(session, List.of(999_999L));

        // then
        assertImageIdsRejected(stolen);
        assertImageIdsRejected(missing);
        assertThat(postIdOfImage(othersImage)).isNull();
    }

    @Test
    @DisplayName("[F-29][CM-06] 이미 다른 글에 붙은 이미지를 붙이면 400 INVALID_INPUT이고 원래 글에 붙은 채 남는다")
    void rejectsImageAttachedToAnotherPost() {
        // given
        long image = uploadedImage(session, SIZE);
        long firstPost = postIdOf(writePost(session, List.of(image)));

        // when
        MvcTestResult second = writePost(session, List.of(image));

        // then
        assertImageIdsRejected(second);
        assertThat(postIdOfImage(image)).isEqualTo(firstPost);
        assertThat(fixture.postCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-29][CM-06] 글을 고치면서 imageIds로 이미지를 통째로 바꾸면 빠진 이미지는 post_id와 순서가 NULL이 되고 새 순서로 응답한다")
    void reviseReplacesImages() {
        // given
        long kept = uploadedImage(session, SIZE);
        long removed = uploadedImage(session, SIZE);
        long added = uploadedImage(session, SIZE);
        long postId = postIdOf(writePost(session, List.of(kept, removed)));

        // when
        MvcTestResult revised = patch(session, postId, "{\"imageIds\":[" + added + "," + kept + "]}");

        // then
        assertThat(revised).hasStatus(HttpStatus.OK);
        assertThat(revised).bodyJson().extractingPath("$.images[0].imageId").isEqualTo((int) added);
        assertThat(revised).bodyJson().extractingPath("$.images[1].imageId").isEqualTo((int) kept);
        assertThat(revised).bodyJson().extractingPath("$.images").asList().hasSize(2);
        assertThat(postIdOfImage(removed)).isNull();
        assertThat(orderOfImage(removed)).isNull();
        assertThat(postIdOfImage(added)).isEqualTo(postId);
        assertThat(orderOfImage(kept)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-29][CM-06] 수정에서 imageIds를 빈 배열로 보내면 이미지를 모두 떼고, 보내지 않으면 이미지를 그대로 둔다")
    void reviseWithEmptyListDetachesAndAbsentKeeps() {
        // given
        long image = uploadedImage(session, SIZE);
        long postId = postIdOf(writePost(session, List.of(image)));

        // when
        MvcTestResult titleOnly = patch(session, postId, "{\"title\":\"새 제목\"}");
        MvcTestResult cleared = patch(session, postId, "{\"imageIds\":[]}");

        // then
        assertThat(titleOnly).bodyJson().extractingPath("$.images").asList().hasSize(1);
        assertThat(cleared).hasStatus(HttpStatus.OK);
        assertThat(cleared).bodyJson().extractingPath("$.images").asList().isEmpty();
        assertThat(postIdOfImage(image)).isNull();
    }

    @Test
    @DisplayName("[F-29][CM-06] 수정에서 imageIds를 null로 보내면 400, 남의 글이면 이미지 검사보다 먼저 403, 없는 글이면 404다")
    void reviseImageIdsErrors() {
        // given
        long postId = fixture.writePost(session, "FREE", "제목", "본문", null);
        Cookie other = fixture.verifiedSession(fixture.saveMember("다른회원"));
        long otherImage = uploadedImage(other, SIZE);

        // when
        MvcTestResult cleared = patch(session, postId, "{\"imageIds\":null}");
        MvcTestResult forbidden = patch(other, postId, "{\"imageIds\":[" + otherImage + "]}");
        MvcTestResult missing = patch(session, 999_999L, "{\"imageIds\":[1]}");

        // then
        assertThat(cleared).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(forbidden).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(postIdOfImage(otherImage)).isNull();
    }

    @Test
    @DisplayName("[F-29][CM-06] 글을 지워도 붙은 이미지는 글에 붙은 채 남는다")
    void deletingPostLeavesImagesAttached() {
        // given
        long image = uploadedImage(session, SIZE);
        long postId = postIdOf(writePost(session, List.of(image)));

        // when
        MvcTestResult deleted = fixture.send(mvc.delete().uri(POSTS + "/" + postId), session, null);

        // then
        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(postIdOfImage(image)).isEqualTo(postId);
    }

    private void assertImageIdsRejected(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(result).bodyJson().extractingPath("$.fieldErrors[0].field").isEqualTo("imageIds");
    }

    private MvcTestResult writePost(Cookie cookie, List<Long> imageIds) {
        return fixture.send(
                mvc.post().uri(POSTS),
                cookie,
                "{\"category\":\"FREE\",\"title\":\"제목\",\"content\":\"본문\",\"imageIds\":%s}".formatted(imageIds));
    }

    private MvcTestResult patch(Cookie cookie, long postId, String body) {
        return fixture.send(mvc.patch().uri(POSTS + "/" + postId), cookie, body);
    }

    private long issueImage(Cookie cookie, long sizeBytes) {
        MvcTestResult result = fixture.send(
                mvc.post().uri(IMAGES), cookie, "{\"contentType\":\"image/jpeg\",\"sizeBytes\":" + sizeBytes + "}");
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return idOf(result);
    }

    // 업로드 URL을 받고, 저장소에 그 크기의 객체가 올라온 것으로 표시한다.
    private long uploadedImage(Cookie cookie, long sizeBytes) {
        long imageId = issueImage(cookie, sizeBytes);
        storage.markUploaded(keyOf(imageId), sizeBytes);
        return imageId;
    }

    private static long idOf(MvcTestResult result) {
        AtomicLong imageId = new AtomicLong();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.imageId")
                .asNumber()
                .satisfies(number -> imageId.set(number.longValue()));
        return imageId.get();
    }

    private String keyOf(long imageId) {
        return jdbc.queryForObject("SELECT object_key FROM community_image WHERE id = ?", String.class, imageId);
    }

    private Long postIdOfImage(long imageId) {
        return jdbc.queryForObject("SELECT post_id FROM community_image WHERE id = ?", Long.class, imageId);
    }

    private Integer orderOfImage(long imageId) {
        return jdbc.queryForObject("SELECT display_order FROM community_image WHERE id = ?", Integer.class, imageId);
    }

    private int imageRowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM community_image", Integer.class);
    }
}
