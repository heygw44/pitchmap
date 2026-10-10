package com.pitchmap.community.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.community.api.CommunityApiFixture.POSTS;
import static com.pitchmap.community.api.CommunityApiFixture.postIdOf;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
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
class CommunityPostApiIntegrationTest {

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
    private MutableClock clock;

    private CommunityApiFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
    }

    @Test
    @DisplayName("[F-29][CM-02] 인증 회원이 글을 쓰면 201이고, 비로그인 사용자의 목록 맨 앞과 상세에 그 글이 보인다")
    void writeThenListAndDetailAsAnonymous() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Member author = fixture.saveMember("새벽능선");
        Cookie session = fixture.verifiedSession(author);
        fixture.writePost(session, "먼저 쓴 글", "첫 본문", null);

        // when
        MvcTestResult created = fixture.send(
                mvc.post().uri(POSTS), session, "{\"title\":\"텐트 후기\",\"content\":\"가볍다\",\"spotId\":" + spotId + "}");
        long postId = postIdOf(created);
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();

        // then
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(list).bodyJson().extractingPath("$.content[0].postId").isEqualTo((int) postId);
        assertThat(list).bodyJson().extractingPath("$.page").isEqualTo(0);
        assertThat(list).bodyJson().extractingPath("$.size").isEqualTo(20);
        assertThat(list).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail).bodyJson().isStrictlyEqualTo("""
                {
                  "postId": %d,
                  "title": "텐트 후기",
                  "content": "가볍다",
                  "images": [],
                  "author": { "memberId": %d, "nickname": "새벽능선" },
                  "spot": { "spotId": %d, "name": "능선 끝 평지" },
                  "likeCount": 0,
                  "commentCount": 0,
                  "createdAt": "2026-10-05T03:00:00Z",
                  "updatedAt": "2026-10-05T03:00:00Z"
                }
                """.formatted(postId, author.getId(), spotId));
    }

    @Test
    @DisplayName("[F-29][CM-09] 목록 항목은 정해진 필드만 담고, 응답 어디에도 작성자의 이메일이 없다")
    void listItemHasOnlyDefinedFieldsAndNoEmail() throws Exception {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Member author = fixture.saveMember("새벽능선");
        long postId = fixture.writePost(fixture.verifiedSession(author), "굴업도", "바다가 좋았다", spotId);

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();

        // then
        assertThat(list).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "postId": %d,
                    "title": "굴업도",
                    "excerpt": "바다가 좋았다",
                    "author": { "memberId": %d, "nickname": "새벽능선" },
                    "spot": { "spotId": %d, "name": "능선 끝 평지" },
                    "imageCount": 0,
                    "likeCount": 0,
                  "commentCount": 0,
                    "createdAt": "2026-10-05T03:00:00Z"
                  }],
                  "page": 0, "size": 20, "hasNext": false, "totalElements": 1, "totalPages": 1
                }
                """.formatted(postId, author.getId(), spotId));
        assertThat(list.getResponse().getContentAsString()).doesNotContain(author.getEmail());
        assertThat(detail.getResponse().getContentAsString()).doesNotContain(author.getEmail());
    }

    @Test
    @DisplayName("[F-29][CM-02] 목록의 excerpt는 본문 앞 100자이고, 상세의 content는 본문 전체다")
    void excerptIsFirstHundredCharacters() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        String content = "가".repeat(100) + "나".repeat(50);
        long postId = fixture.writePost(session, "긴 글", content, null);

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();

        // then
        assertThat(list).bodyJson().extractingPath("$.content[0].excerpt").isEqualTo("가".repeat(100));
        assertThat(detail).bodyJson().extractingPath("$.content").isEqualTo(content);
    }

    @Test
    @DisplayName("[F-29] spotId로 거르면 그 장소에 연결한 글만 돌려주고, 거르지 않으면 모든 글을 한 목록으로 돌려준다")
    void filtersBySpot() {
        // given
        long spotA = fixture.insertSpot("A 장소", "ACTIVE");
        long spotB = fixture.insertSpot("B 장소", "ACTIVE");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long firstA = fixture.writePost(session, "장소 A 첫 글", "본문", spotA);
        long secondA = fixture.writePost(session, "장소 A 둘째 글", "본문", spotA);
        long onB = fixture.writePost(session, "장소 B 글", "본문", spotB);
        long noSpot = fixture.writePost(session, "장소 없는 글", "본문", null);
        long emptySpot = fixture.insertSpot("글 없는 장소", "ACTIVE");

        // when
        MvcTestResult all = mvc.get().uri(POSTS).exchange();
        MvcTestResult bySpot = mvc.get().uri(POSTS + "?spotId=" + spotA).exchange();
        MvcTestResult none = mvc.get().uri(POSTS + "?spotId=" + emptySpot).exchange();

        // then
        assertThat(all)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) noSpot, (int) onB, (int) secondA, (int) firstA);
        assertThat(all).bodyJson().doesNotHavePath("$.content[0].category");
        assertThat(bySpot)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) secondA, (int) firstA);
        assertThat(none).bodyJson().extractingPath("$.content").asList().isEmpty();
    }

    @Test
    @DisplayName("[F-29] 최신 글부터 size건씩 주고, 다음 페이지가 있을 때만 hasNext가 true이다")
    void pagesNewestFirstWithHasNext() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long first = fixture.writePost(session, "첫 글", "본문", null);
        long second = fixture.writePost(session, "둘째 글", "본문", null);
        long third = fixture.writePost(session, "셋째 글", "본문", null);

        // when
        MvcTestResult page0 = mvc.get().uri(POSTS + "?page=0&size=2").exchange();
        MvcTestResult page1 = mvc.get().uri(POSTS + "?page=1&size=2").exchange();
        MvcTestResult exact = mvc.get().uri(POSTS + "?page=0&size=3").exchange();

        // then
        assertThat(page0)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) third, (int) second);
        assertThat(page0).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(page1)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) first);
        assertThat(page1).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(exact).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-29][CM-01] 목록은 조건에 맞는 전체 글 수와 페이지 수를 주고, 마지막 페이지 너머를 요청하면 빈 목록과 같은 페이지 수를 준다")
    void listReportsTotalsAndHandlesPageBeyondLast() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        for (int i = 0; i < 5; i++) {
            fixture.writePost(session, "글 " + i, "본문", i < 3 ? spotId : null);
        }
        long deleted = fixture.writePost(session, "지운 글", "본문", null);
        fixture.send(mvc.delete().uri(POSTS + "/" + deleted), session, null);

        // when
        MvcTestResult first = mvc.get().uri(POSTS + "?page=0&size=2").exchange();
        MvcTestResult last = mvc.get().uri(POSTS + "?page=2&size=2").exchange();
        MvcTestResult beyond = mvc.get().uri(POSTS + "?page=7&size=2").exchange();
        MvcTestResult bySpot =
                mvc.get().uri(POSTS + "?spotId=" + spotId + "&size=2").exchange();
        MvcTestResult emptySpot =
                mvc.get().uri(POSTS + "?spotId=" + (spotId + 1000)).exchange();

        // then
        assertThat(first).bodyJson().extractingPath("$.totalElements").isEqualTo(5);
        assertThat(first).bodyJson().extractingPath("$.totalPages").isEqualTo(3);
        assertThat(first).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(last).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(last).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(beyond).hasStatus(HttpStatus.OK);
        assertThat(beyond).bodyJson().extractingPath("$.content").asList().isEmpty();
        assertThat(beyond).bodyJson().extractingPath("$.page").isEqualTo(7);
        assertThat(beyond).bodyJson().extractingPath("$.totalElements").isEqualTo(5);
        assertThat(beyond).bodyJson().extractingPath("$.totalPages").isEqualTo(3);
        assertThat(beyond).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(bySpot).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
        assertThat(bySpot).bodyJson().extractingPath("$.totalPages").isEqualTo(2);
        assertThat(emptySpot).bodyJson().extractingPath("$.totalElements").isEqualTo(0);
        assertThat(emptySpot).bodyJson().extractingPath("$.totalPages").isEqualTo(0);
    }

    @Test
    @DisplayName("[F-29] 범위를 벗어난 size는 400 INVALID_INPUT이다")
    void listRejectsBadSize() {
        // when
        MvcTestResult tooBig = mvc.get().uri(POSTS + "?size=51").exchange();
        MvcTestResult zero = mvc.get().uri(POSTS + "?size=0").exchange();

        // then
        assertThat(tooBig).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooBig).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(zero).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("[F-29][CM-02] 숨겨졌거나 없는 장소를 연결해 글을 쓰면 400 INVALID_INPUT이고 글은 저장되지 않는다")
    void writeWithHiddenOrMissingSpotIsRejected() {
        // given
        long hidden = fixture.insertSpot("숨김 장소", "HIDDEN");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        String template = "{\"title\":\"제목\",\"content\":\"본문\",\"spotId\":%d}";

        // when
        MvcTestResult linkHidden = fixture.send(mvc.post().uri(POSTS), session, template.formatted(hidden));
        MvcTestResult linkMissing = fixture.send(mvc.post().uri(POSTS), session, template.formatted(999_999L));

        // then
        assertThat(linkHidden).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(linkHidden).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(linkMissing).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(linkMissing).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(fixture.postCount()).isZero();
    }

    @Test
    @DisplayName("[F-29][CM-02] 연결한 장소가 나중에 숨겨지면 글은 그대로 두고 목록과 상세에서 spot 필드만 빠진다")
    void spotFieldDisappearsWhenLinkedSpotIsHiddenLater() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", spotId);
        fixture.setSpotStatus(spotId, "HIDDEN");

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();
        MvcTestResult filtered = mvc.get().uri(POSTS + "?spotId=" + spotId).exchange();

        // then
        assertThat(list).bodyJson().extractingPath("$.content[0].postId").isEqualTo((int) postId);
        assertThat(list).bodyJson().doesNotHavePath("$.content[0].spot");
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat(detail).bodyJson().doesNotHavePath("$.spot");
        assertThat(filtered).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(filtered).bodyJson().doesNotHavePath("$.content[0].spot");
    }

    @Test
    @DisplayName("[F-29][CM-03] 작성자가 글을 고치면 보낸 필드만 바뀌고 수정 시각이 갱신된다")
    void authorRevisesOnlyGivenFields() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        Cookie session = fixture.verifiedSession(fixture.saveMember("새벽능선"));
        long postId = fixture.writePost(session, "제목", "본문", spotId);
        clock.advance(Duration.ofHours(1));

        // when
        MvcTestResult result = fixture.send(mvc.patch().uri(POSTS + "/" + postId), session, "{\"title\":\"새 제목\"}");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("새 제목");
        assertThat(result).bodyJson().extractingPath("$.content").isEqualTo("본문");
        assertThat(result).bodyJson().extractingPath("$.spot.spotId").isEqualTo((int) spotId);
        assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-10-05T03:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.updatedAt").isEqualTo("2026-10-05T04:00:00Z");
    }

    @Test
    @DisplayName("[F-29][CM-02] spotId를 null로 보내면 장소 연결이 끊기고, ACTIVE가 아닌 장소를 보내면 400이다")
    void patchDisconnectsOrRelinksSpot() {
        // given
        long spotId = fixture.insertSpot("능선 끝 평지", "ACTIVE");
        long hidden = fixture.insertSpot("숨김 장소", "HIDDEN");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", spotId);

        // when
        MvcTestResult relinkHidden =
                fixture.send(mvc.patch().uri(POSTS + "/" + postId), session, "{\"spotId\":" + hidden + "}");
        MvcTestResult disconnect = fixture.send(mvc.patch().uri(POSTS + "/" + postId), session, "{\"spotId\":null}");

        // then
        assertThat(relinkHidden).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(relinkHidden).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        assertThat(disconnect).hasStatus(HttpStatus.OK);
        assertThat(disconnect).bodyJson().doesNotHavePath("$.spot");
        assertThat(mvc.get().uri(POSTS + "/" + postId).exchange()).bodyJson().doesNotHavePath("$.spot");
    }

    @Test
    @DisplayName("[F-29][CM-03] 빈 JSON 객체로 고치면 200이고 아무것도 바뀌지 않으며, title·content를 null로 보내면 400이다")
    void patchEmptyObjectAndExplicitNulls() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        clock.advance(Duration.ofHours(1));
        String uri = POSTS + "/" + postId;

        // when
        MvcTestResult empty = fixture.send(mvc.patch().uri(uri), session, "{}");
        MvcTestResult nullTitle = fixture.send(mvc.patch().uri(uri), session, "{\"title\":null}");
        MvcTestResult nullContent = fixture.send(mvc.patch().uri(uri), session, "{\"content\":null}");
        MvcTestResult blankTitle = fixture.send(mvc.patch().uri(uri), session, "{\"title\":\"   \"}");
        MvcTestResult longContent =
                fixture.send(mvc.patch().uri(uri), session, "{\"content\":\"" + "가".repeat(10001) + "\"}");

        // then
        assertThat(empty).hasStatus(HttpStatus.OK);
        assertThat(empty).bodyJson().extractingPath("$.title").isEqualTo("제목");
        assertThat(empty).bodyJson().extractingPath("$.updatedAt").isEqualTo("2026-10-05T03:00:00Z");
        for (MvcTestResult rejected : new MvcTestResult[] {nullTitle, nullContent, blankTitle, longContent}) {
            assertThat(rejected).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(rejected).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(mvc.get().uri(uri).exchange())
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("제목");
    }

    @Test
    @DisplayName("[F-29][CM-03] 다른 회원이 고치거나 지우면 403 ACCESS_DENIED이고 글은 그대로다")
    void otherMemberCannotReviseOrDelete() {
        // given
        Cookie authorSession = fixture.verifiedSession(fixture.saveMember(null));
        Cookie otherSession = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(authorSession, "제목", "본문", null);
        String uri = POSTS + "/" + postId;

        // when
        MvcTestResult patch = fixture.send(mvc.patch().uri(uri), otherSession, "{\"title\":\"가로채기\"}");
        MvcTestResult delete = fixture.send(mvc.delete().uri(uri), otherSession, null);

        // then
        assertThat(patch).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patch).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(mvc.get().uri(uri).exchange())
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("제목");
        assertThat(fixture.postStatus(postId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-29][CM-03] 글을 지우면 204이고 행은 DELETED로 남으며, 이후 목록에서 빠지고 상세·수정·삭제는 작성자에게도 404다")
    void deletedPostIsHiddenAndKeptAsRow() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        String uri = POSTS + "/" + postId;

        // when
        MvcTestResult delete = fixture.send(mvc.delete().uri(uri), session, null);
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(uri).exchange();
        MvcTestResult patch = fixture.send(mvc.patch().uri(uri), session, "{\"title\":\"다시\"}");
        MvcTestResult deleteAgain = fixture.send(mvc.delete().uri(uri), session, null);

        // then
        assertThat(delete).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(fixture.postStatus(postId)).isEqualTo("DELETED");
        assertThat(fixture.postCount()).isEqualTo(1);
        assertThat(list).bodyJson().extractingPath("$.content").asList().isEmpty();
        assertThat(detail).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(detail).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(patch).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(deleteAgain).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-29][CM-03] 숨김이나 검토 대기 글은 작성자에게도 없는 글이다. 다른 회원이 건드릴 때도 403이 아니라 404다")
    void hiddenPostIsNotFoundForEveryone() {
        // given
        Cookie authorSession = fixture.verifiedSession(fixture.saveMember(null));
        Cookie otherSession = fixture.verifiedSession(fixture.saveMember(null));
        long hiddenId = fixture.writePost(authorSession, "숨김", "본문", null);
        long pendingId = fixture.writePost(authorSession, "검토 대기", "본문", null);
        jdbc.update("UPDATE community_post SET status = 'HIDDEN' WHERE id = ?", hiddenId);
        jdbc.update("UPDATE community_post SET status = 'PENDING_REVIEW' WHERE id = ?", pendingId);

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult hiddenDetail = mvc.get().uri(POSTS + "/" + hiddenId).exchange();
        MvcTestResult pendingPatchByAuthor =
                fixture.send(mvc.patch().uri(POSTS + "/" + pendingId), authorSession, "{}");
        MvcTestResult hiddenDeleteByOther = fixture.send(mvc.delete().uri(POSTS + "/" + hiddenId), otherSession, null);

        // then
        assertThat(list).bodyJson().extractingPath("$.content").asList().isEmpty();
        assertThat(hiddenDetail).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(pendingPatchByAuthor).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(hiddenDeleteByOther).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-29][CM-09][PV-08] 작성자가 탈퇴하면 목록과 상세에 익명 닉네임 탈퇴회원_{id}로 보인다")
    void withdrawnAuthorShowsAnonymizedNickname() {
        // given
        Member author = fixture.saveMember("새벽능선");
        long postId = fixture.writePost(fixture.verifiedSession(author), "제목", "본문", null);
        Member loaded = memberRepository.findById(author.getId()).orElseThrow();
        loaded.withdraw(MutableClock.DEFAULT_INSTANT);
        memberRepository.saveAndFlush(loaded);
        String anonymized = "탈퇴회원_" + author.getId();

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();

        // then
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo(anonymized);
        assertThat(detail).bodyJson().extractingPath("$.author.nickname").isEqualTo(anonymized);
        assertThat(detail).bodyJson().extractingPath("$.author.memberId").isEqualTo((int) (long) author.getId());
    }

    @Test
    @DisplayName("[F-29][TR-03] 비로그인 쓰기는 401, 이메일 인증 전 회원의 쓰기는 403 MEMBER_NOT_VERIFIED이고 글은 저장되지 않는다")
    void writeRequiresVerifiedMember() {
        // given
        String body = "{\"title\":\"제목\",\"content\":\"본문\"}";
        Member unverified = fixture.saveMember(null);
        Cookie unverifiedSession = loginOnly(unverified);

        // when
        MvcTestResult anonymous = mvc.post()
                .uri(POSTS)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        MvcTestResult notVerified = fixture.send(mvc.post().uri(POSTS), unverifiedSession, body);

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(notVerified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(notVerified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(fixture.postCount()).isZero();
    }

    // 이메일 인증 없이 로그인만 한 세션을 만든다.
    private Cookie loginOnly(Member member) {
        MvcTestResult login = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"Valid-pass1\"}".formatted(member.getEmail()))
                .exchange();
        assertThat(login).hasStatus(HttpStatus.OK);
        return login.getResponse().getCookie("SESSION");
    }
}
