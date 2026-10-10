package com.pitchmap.community.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.community.api.CommunityApiFixture.POSTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.InstanceOfAssertFactories;
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
class CommunityCommentApiIntegrationTest {

    private static final String COMMENT_URI = "/api/community/comments/";

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

    private CommunityApiFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(mvc, memberRepository, passwordEncoder, emailVerificationService, jdbc);
    }

    @Test
    @DisplayName("[F-29][CM-04] 댓글을 쓰고 그 댓글에 답글을 쓰면, 비로그인 사용자의 목록에서 답글이 부모 댓글 아래에 묶여 나온다")
    void commentThenReplyIsNestedInList() {
        // given
        Member author = fixture.saveMember("새벽능선");
        Member replier = fixture.saveMember("아침안개");
        Cookie authorSession = fixture.verifiedSession(author);
        long postId = fixture.writePost(authorSession, "제목", "본문", null);
        long commentId = fixture.writeComment(authorSession, postId, "좋은 글이에요", null);

        // when
        long replyId = fixture.writeComment(fixture.verifiedSession(replier), postId, "저도요", commentId);
        MvcTestResult list = mvc.get().uri(commentsUri(postId)).exchange();

        // then
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "commentId": %d,
                    "author": { "memberId": %d, "nickname": "새벽능선" },
                    "content": "좋은 글이에요",
                    "deleted": false,
                    "createdAt": "2026-10-05T03:00:00Z",
                    "updatedAt": "2026-10-05T03:00:00Z",
                    "replies": [{
                      "commentId": %d,
                      "author": { "memberId": %d, "nickname": "아침안개" },
                      "content": "저도요",
                      "deleted": false,
                      "createdAt": "2026-10-05T03:00:00Z",
                      "updatedAt": "2026-10-05T03:00:00Z",
                      "replies": []
                    }]
                  }],
                  "page": 0, "size": 20, "hasNext": false
                }
                """.formatted(
                        commentId, author.getId(), replyId, replier.getId()));
    }

    @Test
    @DisplayName("[F-29][CM-04] 답글에 다는 답글, 다른 글의 댓글을 부모로 한 답글, 없는 부모 ID, ACTIVE가 아닌 부모는 400 INVALID_INPUT이고 저장되지 않는다")
    void invalidParentIsRejected() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        long otherPostId = fixture.writePost(session, "다른 글", "본문", null);
        long parentId = fixture.writeComment(session, postId, "댓글", null);
        long replyId = fixture.writeComment(session, postId, "답글", parentId);
        long otherPostCommentId = fixture.writeComment(session, otherPostId, "다른 글 댓글", null);
        long hiddenParentId = fixture.writeComment(session, postId, "숨길 댓글", null);
        fixture.setCommentStatus(hiddenParentId, "HIDDEN");
        long deletedParentId = fixture.writeComment(session, postId, "지울 댓글", null);
        fixture.send(mvc.delete().uri(COMMENT_URI + deletedParentId), session, null);
        int before = fixture.commentCount();

        // when
        MvcTestResult toReply = fixture.writeCommentRaw(session, postId, "답글의 답글", replyId);
        MvcTestResult toOtherPost = fixture.writeCommentRaw(session, postId, "다른 글 부모", otherPostCommentId);
        MvcTestResult toMissing = fixture.writeCommentRaw(session, postId, "없는 부모", 999999L);
        MvcTestResult toHidden = fixture.writeCommentRaw(session, postId, "숨김 부모", hiddenParentId);
        MvcTestResult toDeleted = fixture.writeCommentRaw(session, postId, "삭제 부모", deletedParentId);

        // then
        for (MvcTestResult result : new MvcTestResult[] {toReply, toOtherPost, toMissing, toHidden, toDeleted}) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(fixture.commentCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("[F-29][CM-04] 답글이 달린 댓글을 지우면 내용은 지워지고 목록에 자리(commentId, deleted, replies)만 남으며 답글은 그대로 보인다")
    void deletingCommentWithRepliesLeavesPlaceholder() {
        // given
        Member replier = fixture.saveMember("아침안개");
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        long commentId = fixture.writeComment(session, postId, "지울 댓글", null);
        long replyId = fixture.writeComment(fixture.verifiedSession(replier), postId, "남을 답글", commentId);

        // when
        MvcTestResult delete = fixture.send(mvc.delete().uri(COMMENT_URI + commentId), session, null);
        MvcTestResult list = mvc.get().uri(commentsUri(postId)).exchange();

        // then
        assertThat(delete).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(fixture.commentStatus(commentId)).isEqualTo("DELETED");
        assertThat(fixture.commentContent(commentId)).isNull();
        assertThat(list).bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{
                    "commentId": %d,
                    "deleted": true,
                    "replies": [{
                      "commentId": %d,
                      "author": { "memberId": %d, "nickname": "아침안개" },
                      "content": "남을 답글",
                      "deleted": false,
                      "createdAt": "2026-10-05T03:00:00Z",
                      "updatedAt": "2026-10-05T03:00:00Z",
                      "replies": []
                    }]
                  }],
                  "page": 0, "size": 20, "hasNext": false
                }
                """.formatted(commentId, replyId, replier.getId()));
    }

    @Test
    @DisplayName("[F-29][CM-04] 답글이 없는 댓글을 지우면 행은 DELETED로 남지만 내용은 지워지고 목록에서 사라진다")
    void deletingCommentWithoutRepliesRemovesItFromList() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        long commentId = fixture.writeComment(session, postId, "지울 댓글", null);
        fixture.writeComment(session, postId, "남는 댓글", null);

        // when
        MvcTestResult delete = fixture.send(mvc.delete().uri(COMMENT_URI + commentId), session, null);
        MvcTestResult list = mvc.get().uri(commentsUri(postId)).exchange();

        // then
        assertThat(delete).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(fixture.commentStatus(commentId)).isEqualTo("DELETED");
        assertThat(fixture.commentContent(commentId)).isNull();
        assertThat(fixture.commentCount()).isEqualTo(2);
        assertThat(list).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(list).bodyJson().extractingPath("$.content[0].content").isEqualTo("남는 댓글");
    }

    @Test
    @DisplayName("[F-29][CM-04] 숨김 댓글도 삭제와 같은 규칙이다. 보이는 답글이 있으면 자리만 남고 author·content가 없으며, 없으면 목록에서 빠진다")
    void hiddenCommentFollowsSameRules() throws Exception {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        long withReplyId = fixture.writeComment(session, postId, "숨길 댓글", null);
        long replyId = fixture.writeComment(session, postId, "답글", withReplyId);
        long withoutReplyId = fixture.writeComment(session, postId, "답글 없는 숨김 댓글", null);
        fixture.setCommentStatus(withReplyId, "HIDDEN");
        fixture.setCommentStatus(withoutReplyId, "PENDING_REVIEW");

        // when
        MvcTestResult list = mvc.get().uri(commentsUri(postId)).exchange();

        // then
        assertThat(list).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(list).bodyJson().extractingPath("$.content[0].commentId").isEqualTo((int) withReplyId);
        assertThat(list).bodyJson().extractingPath("$.content[0].deleted").isEqualTo(true);
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0].replies[0].commentId")
                .isEqualTo((int) replyId);
        String body = list.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("숨길 댓글").doesNotContain("답글 없는 숨김 댓글");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0]")
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsOnlyKeys("commentId", "deleted", "replies");
    }

    @Test
    @DisplayName("[F-29][CM-04] 목록의 page·size와 hasNext는 최상위 댓글 단위이고, 답글 없는 숨김 댓글이 섞여도 틀리지 않는다")
    void pagingCountsVisibleTopLevelCommentsOnly() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        long first = fixture.writeComment(session, postId, "첫째", null);
        fixture.writeComment(session, postId, "첫째의 답글 1", first);
        fixture.writeComment(session, postId, "첫째의 답글 2", first);
        long hidden = fixture.writeComment(session, postId, "숨길 댓글", null);
        long third = fixture.writeComment(session, postId, "셋째", null);
        long fourth = fixture.writeComment(session, postId, "넷째", null);
        fixture.setCommentStatus(hidden, "HIDDEN");

        // when
        MvcTestResult page0 =
                mvc.get().uri(commentsUri(postId) + "?page=0&size=2").exchange();
        MvcTestResult page1 =
                mvc.get().uri(commentsUri(postId) + "?page=1&size=2").exchange();
        MvcTestResult page2 =
                mvc.get().uri(commentsUri(postId) + "?page=2&size=2").exchange();

        // then
        assertThat(page0).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(page0).bodyJson().extractingPath("$.content[0].commentId").isEqualTo((int) first);
        assertThat(page0)
                .bodyJson()
                .extractingPath("$.content[0].replies")
                .asList()
                .hasSize(2);
        assertThat(page0).bodyJson().extractingPath("$.content[1].commentId").isEqualTo((int) third);
        assertThat(page0).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(page1).bodyJson().extractingPath("$.content").asList().hasSize(1);
        assertThat(page1).bodyJson().extractingPath("$.content[0].commentId").isEqualTo((int) fourth);
        assertThat(page1).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
        assertThat(page2).bodyJson().extractingPath("$.content").asList().isEmpty();
    }

    @Test
    @DisplayName("[F-29][CM-04] 답글 없는 숨김 댓글이 마지막에 있어도 다음 페이지가 있다고 하지 않는다")
    void hasNextIsFalseWhenOnlyHiddenCommentRemains() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        fixture.writeComment(session, postId, "첫째", null);
        fixture.writeComment(session, postId, "둘째", null);
        long hidden = fixture.writeComment(session, postId, "숨길 댓글", null);
        fixture.setCommentStatus(hidden, "HIDDEN");

        // when
        MvcTestResult list = mvc.get().uri(commentsUri(postId) + "?size=2").exchange();

        // then
        assertThat(list).bodyJson().extractingPath("$.content").asList().hasSize(2);
        assertThat(list).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-29] 작성자가 댓글을 고치면 200과 고친 항목을 응답하고, 최상위 댓글이면 답글도 함께 담긴다")
    void authorRevisesComment() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        long commentId = fixture.writeComment(session, postId, "처음", null);
        long replyId = fixture.writeComment(session, postId, "답글", commentId);

        // when
        MvcTestResult revisedTop =
                fixture.send(mvc.patch().uri(COMMENT_URI + commentId), session, "{\"content\":\"고침\"}");
        MvcTestResult revisedReply =
                fixture.send(mvc.patch().uri(COMMENT_URI + replyId), session, "{\"content\":\"답글 고침\"}");

        // then
        assertThat(revisedTop).hasStatus(HttpStatus.OK);
        assertThat(revisedTop).bodyJson().extractingPath("$.content").isEqualTo("고침");
        assertThat(revisedTop).bodyJson().extractingPath("$.deleted").isEqualTo(false);
        assertThat(revisedTop).bodyJson().extractingPath("$.replies").asList().hasSize(1);
        assertThat(revisedTop).bodyJson().extractingPath("$.replies[0].content").isEqualTo("답글");
        assertThat(revisedReply).hasStatus(HttpStatus.OK);
        assertThat(revisedReply).bodyJson().extractingPath("$.content").isEqualTo("답글 고침");
        assertThat(revisedReply).bodyJson().extractingPath("$.replies").asList().isEmpty();
    }

    @Test
    @DisplayName("[F-29] 수정에서 내용이 비었거나 1001자이면 400 INVALID_INPUT이고 내용은 그대로다")
    void reviseRejectsInvalidContent() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        long commentId = fixture.writeComment(session, postId, "처음", null);

        // when
        MvcTestResult blank = fixture.send(mvc.patch().uri(COMMENT_URI + commentId), session, "{\"content\":\" \"}");
        MvcTestResult tooLong = fixture.send(
                mvc.patch().uri(COMMENT_URI + commentId), session, "{\"content\":\"" + "가".repeat(1001) + "\"}");

        // then
        assertThat(blank).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(tooLong).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(fixture.commentContent(commentId)).isEqualTo("처음");
    }

    @Test
    @DisplayName("[F-29][CM-03] 다른 회원이 댓글을 고치거나 지우면 403 ACCESS_DENIED이고 댓글은 그대로다")
    void otherMemberCannotReviseOrDelete() {
        // given
        Cookie authorSession = fixture.verifiedSession(fixture.saveMember(null));
        Cookie otherSession = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(authorSession, "제목", "본문", null);
        long commentId = fixture.writeComment(authorSession, postId, "내 댓글", null);

        // when
        MvcTestResult patch =
                fixture.send(mvc.patch().uri(COMMENT_URI + commentId), otherSession, "{\"content\":\"가로채기\"}");
        MvcTestResult delete = fixture.send(mvc.delete().uri(COMMENT_URI + commentId), otherSession, null);

        // then
        assertThat(patch).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(patch).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        assertThat(delete).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(fixture.commentContent(commentId)).isEqualTo("내 댓글");
        assertThat(fixture.commentStatus(commentId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-29][CM-03] 지운 댓글이나 숨긴 댓글의 수정·삭제는 작성자에게도, 다른 회원에게도 404 NOT_FOUND다")
    void inactiveCommentIsNotFound() {
        // given
        Cookie authorSession = fixture.verifiedSession(fixture.saveMember(null));
        Cookie otherSession = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(authorSession, "제목", "본문", null);
        long deletedId = fixture.writeComment(authorSession, postId, "지울 댓글", null);
        long hiddenId = fixture.writeComment(authorSession, postId, "숨길 댓글", null);
        fixture.send(mvc.delete().uri(COMMENT_URI + deletedId), authorSession, null);
        fixture.setCommentStatus(hiddenId, "HIDDEN");

        // when
        MvcTestResult patchDeleted =
                fixture.send(mvc.patch().uri(COMMENT_URI + deletedId), authorSession, "{\"content\":\"다시\"}");
        MvcTestResult deleteDeleted = fixture.send(mvc.delete().uri(COMMENT_URI + deletedId), authorSession, null);
        MvcTestResult patchHidden =
                fixture.send(mvc.patch().uri(COMMENT_URI + hiddenId), authorSession, "{\"content\":\"다시\"}");
        MvcTestResult deleteHiddenByOther = fixture.send(mvc.delete().uri(COMMENT_URI + hiddenId), otherSession, null);
        MvcTestResult patchMissing =
                fixture.send(mvc.patch().uri(COMMENT_URI + 999999), authorSession, "{\"content\":\"다시\"}");

        // then
        assertThat(patchDeleted).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(patchDeleted).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(deleteDeleted).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(patchHidden).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(deleteHiddenByOther).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(patchMissing).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-29][CM-03] 글을 지우거나 숨기면 그 글의 댓글 목록·작성은 404이고, 댓글 수정·삭제도 작성자에게 404다")
    void commentsOfInactivePostAreNotFound() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long deletedPostId = fixture.writePost(session, "지울 글", "본문", null);
        long deletedPostCommentId = fixture.writeComment(session, deletedPostId, "댓글", null);
        long hiddenPostId = fixture.writePost(session, "숨길 글", "본문", null);
        long hiddenPostCommentId = fixture.writeComment(session, hiddenPostId, "댓글", null);
        fixture.send(mvc.delete().uri(POSTS + "/" + deletedPostId), session, null);
        jdbc.update("UPDATE community_post SET status = 'HIDDEN' WHERE id = ?", hiddenPostId);

        // when
        MvcTestResult list = mvc.get().uri(commentsUri(deletedPostId)).exchange();
        MvcTestResult hiddenList = mvc.get().uri(commentsUri(hiddenPostId)).exchange();
        MvcTestResult create = fixture.writeCommentRaw(session, deletedPostId, "새 댓글", null);
        MvcTestResult createOnHidden = fixture.writeCommentRaw(session, hiddenPostId, "새 댓글", null);
        MvcTestResult patch =
                fixture.send(mvc.patch().uri(COMMENT_URI + deletedPostCommentId), session, "{\"content\":\"다시\"}");
        MvcTestResult delete = fixture.send(mvc.delete().uri(COMMENT_URI + hiddenPostCommentId), session, null);
        MvcTestResult missingPost = mvc.get().uri(commentsUri(999999)).exchange();

        // then
        for (MvcTestResult result :
                new MvcTestResult[] {list, hiddenList, create, createOnHidden, patch, delete, missingPost}) {
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
        assertThat(fixture.commentStatus(deletedPostCommentId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-29][CM-04] 글 목록과 상세의 commentCount는 ACTIVE인 댓글과 답글의 수다. 지우거나 숨기면 줄어든다")
    void postCommentCountCountsActiveCommentsAndReplies() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(session, "제목", "본문", null);
        long otherPostId = fixture.writePost(session, "다른 글", "본문", null);
        fixture.writeComment(session, otherPostId, "다른 글 댓글", null);
        long a = fixture.writeComment(session, postId, "댓글 A", null);
        long b = fixture.writeComment(session, postId, "답글 B", a);
        long c = fixture.writeComment(session, postId, "댓글 C", null);
        fixture.writeComment(session, postId, "답글 D", c);
        fixture.writeComment(session, postId, "숨길 댓글 E", null);
        fixture.setCommentStatus(fixture.writeComment(session, postId, "숨길 댓글 F", null), "HIDDEN");

        // when
        MvcTestResult before = mvc.get().uri(POSTS + "/" + postId).exchange();
        fixture.send(mvc.delete().uri(COMMENT_URI + b), session, null);
        fixture.send(mvc.delete().uri(COMMENT_URI + c), session, null);
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();
        MvcTestResult list = mvc.get().uri(POSTS).exchange();

        // then
        assertThat(before).bodyJson().extractingPath("$.commentCount").isEqualTo(5);
        assertThat(detail).bodyJson().extractingPath("$.commentCount").isEqualTo(3);
        assertThat(list).bodyJson().extractingPath("$.content[0].postId").isEqualTo((int) otherPostId);
        assertThat(list).bodyJson().extractingPath("$.content[0].commentCount").isEqualTo(1);
        assertThat(list).bodyJson().extractingPath("$.content[1].postId").isEqualTo((int) postId);
        assertThat(list).bodyJson().extractingPath("$.content[1].commentCount").isEqualTo(3);
    }

    @Test
    @DisplayName("[F-29][CM-09][PV-08] 작성자가 탈퇴하면 댓글에 탈퇴회원_{id}로 보이고, 응답 어디에도 이메일이 없다")
    void withdrawnAuthorShowsAnonymizedNicknameAndNoEmail() throws Exception {
        // given
        Member author = fixture.saveMember("새벽능선");
        Cookie session = fixture.verifiedSession(author);
        long postId = fixture.writePost(session, "제목", "본문", null);
        long commentId = fixture.writeComment(session, postId, "댓글", null);
        MvcTestResult beforeWithdraw = mvc.get().uri(commentsUri(postId)).exchange();
        Member loaded = memberRepository.findById(author.getId()).orElseThrow();
        loaded.withdraw(MutableClock.DEFAULT_INSTANT);
        memberRepository.saveAndFlush(loaded);

        // when
        MvcTestResult list = mvc.get().uri(commentsUri(postId)).exchange();
        MvcTestResult revised = fixture.send(mvc.patch().uri(COMMENT_URI + commentId), session, "{\"content\":\"고침\"}");

        // then
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo("탈퇴회원_" + author.getId());
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0].author.memberId")
                .isEqualTo((int) (long) author.getId());
        assertThat(beforeWithdraw.getResponse().getContentAsString()).doesNotContain(author.getEmail());
        assertThat(list.getResponse().getContentAsString()).doesNotContain(author.getEmail());
        assertThat(revised.getResponse().getContentAsString()).doesNotContain(author.getEmail());
    }

    @Test
    @DisplayName("[F-29][TR-03] 비로그인 쓰기는 401, 이메일 인증 전 회원의 쓰기는 403 MEMBER_NOT_VERIFIED이고 댓글은 저장되지 않는다")
    void writeRequiresVerifiedMember() {
        // given
        Cookie authorSession = fixture.verifiedSession(fixture.saveMember(null));
        long postId = fixture.writePost(authorSession, "제목", "본문", null);
        Member unverified = fixture.saveMember(null);

        // when
        MvcTestResult anonymous = mvc.post()
                .uri(commentsUri(postId))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"댓글\"}")
                .exchange();
        MvcTestResult notVerified = fixture.writeCommentRaw(fixture.loginOnly(unverified), postId, "댓글", null);

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(notVerified).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(notVerified).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(fixture.commentCount()).isZero();
    }

    private static String commentsUri(long postId) {
        return POSTS + "/" + postId + "/comments";
    }
}
