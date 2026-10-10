package com.pitchmap.community.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static com.pitchmap.community.api.CommunityApiFixture.POSTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
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
class CommunityReportApiIntegrationTest {

    private static final String COMMENTS = "/api/community/comments/";
    private static final String BODY = "{\"reason\":\"SPAM\",\"content\":\"광고입니다\"}";

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
    @DisplayName("[F-29][CM-07] 인증 회원이 글을 신고하면 201이고 본문이 없으며 신고 행에 사유와 내용이 저장된다")
    void reportPostCreatesRow() {
        // given
        Cookie author = session();
        Cookie reporter = session();
        long postId = fixture.writePost(author, "제목", "본문", null);

        // when
        MvcTestResult result = report(postId, reporter, BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(target_type, ':', reason, ':', content) FROM community_report"
                                + " WHERE target_id = ?",
                        String.class,
                        postId))
                .isEqualTo("POST:SPAM:광고입니다");
        assertThat(fixture.postStatus(postId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("[F-29][CM-07] 같은 회원이 같은 글을 다시 신고하면 409 COMMUNITY_ALREADY_REPORTED이고 신고 행은 하나다")
    void reportingTwiceIsConflict() {
        // given
        Cookie reporter = session();
        long postId = fixture.writePost(session(), "제목", "본문", null);
        assertThat(report(postId, reporter, BODY)).hasStatus(HttpStatus.CREATED);

        // when
        MvcTestResult again = report(postId, reporter, BODY);

        // then
        assertThat(again).hasStatus(HttpStatus.CONFLICT);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("COMMUNITY_ALREADY_REPORTED");
        assertThat(reportRows("POST", postId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-29][CM-07] 작성자가 자기 글을 신고해도 막지 않는다")
    void authorCanReportOwnPost() {
        // given
        Cookie author = session();
        long postId = fixture.writePost(author, "제목", "본문", null);

        // when
        MvcTestResult result = report(postId, author, BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("[F-29][CM-07] 검토 전 신고가 5건이 되면 글이 PENDING_REVIEW가 되어 상세는 404이고 목록에서 빠지며, 그 뒤 신고는 404다")
    void fifthReportMovesPostToPendingReview() {
        // given
        long postId = fixture.writePost(session(), "신고 글", "본문", null);
        long otherId = fixture.writePost(session(), "멀쩡한 글", "본문", null);
        List<Cookie> reporters = sessions(6);
        for (int i = 0; i < 4; i++) {
            assertThat(report(postId, reporters.get(i), BODY)).hasStatus(HttpStatus.CREATED);
        }
        assertThat(fixture.postStatus(postId)).isEqualTo("ACTIVE");

        // when
        MvcTestResult fifth = report(postId, reporters.get(4), BODY);
        MvcTestResult detail = mvc.get().uri(POSTS + "/" + postId).exchange();
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult sixth = report(postId, reporters.get(5), BODY);

        // then
        assertThat(fifth).hasStatus(HttpStatus.CREATED);
        assertThat(fixture.postStatus(postId)).isEqualTo("PENDING_REVIEW");
        assertThat(detail).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(list).hasStatus(HttpStatus.OK);
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[*].postId")
                .asList()
                .containsExactly((int) otherId);
        assertThat(sixth).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(sixth).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        assertThat(reportRows("POST", postId)).isEqualTo(5);
    }

    @Test
    @DisplayName("[F-29][CM-07] 없거나 삭제된 글과 숨긴 글을 신고하면 404 NOT_FOUND다")
    void unavailablePostIsNotFound() {
        // given
        Cookie author = session();
        Cookie reporter = session();
        long deletedId = fixture.writePost(author, "지운 글", "본문", null);
        long hiddenId = fixture.writePost(author, "숨긴 글", "본문", null);
        fixture.send(mvc.delete().uri(POSTS + "/" + deletedId), author, null);
        jdbc.update("UPDATE community_post SET status = 'HIDDEN' WHERE id = ?", hiddenId);

        // when
        MvcTestResult deleted = report(deletedId, reporter, BODY);
        MvcTestResult hidden = report(hiddenId, reporter, BODY);
        MvcTestResult missing = report(999_999L, reporter, BODY);

        // then
        for (MvcTestResult result : List.of(deleted, hidden, missing)) {
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
    }

    @Test
    @DisplayName("[F-29][CM-07] 모르는 신고 사유와 reason이 없는 요청은 400 INVALID_INPUT이고 신고 행이 생기지 않는다")
    void invalidReasonIsBadRequest() {
        // given
        Cookie reporter = session();
        long postId = fixture.writePost(session(), "제목", "본문", null);

        // when
        MvcTestResult unknown = report(postId, reporter, "{\"reason\":\"UNKNOWN\"}");
        MvcTestResult lowerCase = report(postId, reporter, "{\"reason\":\"spam\"}");
        MvcTestResult missing = report(postId, reporter, "{\"content\":\"내용\"}");

        // then
        for (MvcTestResult result : List.of(unknown, lowerCase, missing)) {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
        assertThat(reportRows("POST", postId)).isZero();
    }

    @Test
    @DisplayName("[F-29][CM-07] 이메일 인증을 마치지 않은 회원은 403 MEMBER_NOT_VERIFIED, 로그인하지 않은 사용자는 401이다")
    void requiresVerifiedMember() {
        // given
        long postId = fixture.writePost(session(), "제목", "본문", null);
        Cookie unverified = fixture.loginOnly(fixture.saveMember(TestSequence.nickname()));

        // when
        MvcTestResult forbidden = report(postId, unverified, BODY);
        MvcTestResult anonymous = mvc.post()
                .uri(POSTS + "/" + postId + "/reports")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .exchange();

        // then
        assertThat(forbidden).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(forbidden).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("[F-29][CM-07] 댓글을 5번 신고하면 PENDING_REVIEW가 되고, 보이는 답글이 있는 댓글은 자리만 남고 답글이 없는 댓글은 목록에서 사라진다")
    void fifthReportOnCommentHidesItFromList() {
        // given
        Cookie author = session();
        long postId = fixture.writePost(author, "제목", "본문", null);
        long withReply = fixture.writeComment(author, postId, "답글 달린 댓글", null);
        long replyId = fixture.writeComment(author, postId, "답글", withReply);
        long withoutReply = fixture.writeComment(author, postId, "답글 없는 댓글", null);
        long survivor = fixture.writeComment(author, postId, "멀쩡한 댓글", null);
        List<Cookie> reporters = sessions(5);

        // when
        for (Cookie reporter : reporters) {
            assertThat(reportComment(withReply, reporter, BODY)).hasStatus(HttpStatus.CREATED);
            assertThat(reportComment(withoutReply, reporter, BODY)).hasStatus(HttpStatus.CREATED);
        }
        MvcTestResult list = mvc.get().uri(POSTS + "/" + postId + "/comments").exchange();

        // then
        assertThat(fixture.commentStatus(withReply)).isEqualTo("PENDING_REVIEW");
        assertThat(fixture.commentStatus(withoutReply)).isEqualTo("PENDING_REVIEW");
        assertThat(fixture.commentStatus(survivor)).isEqualTo("ACTIVE");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[*].commentId")
                .asList()
                .containsExactly((int) withReply, (int) survivor);
        assertThat(list).bodyJson().extractingPath("$.content[0].deleted").isEqualTo(true);
        assertThat(list).bodyJson().doesNotHavePath("$.content[0].content");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.content[0].replies[0].commentId")
                .isEqualTo((int) replyId);
        assertThat(reportComment(withReply, session(), BODY)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-29][CM-07] 같은 회원이 같은 댓글을 다시 신고하면 409 COMMUNITY_ALREADY_REPORTED다")
    void reportingCommentTwiceIsConflict() {
        // given
        Cookie author = session();
        Cookie reporter = session();
        long postId = fixture.writePost(author, "제목", "본문", null);
        long commentId = fixture.writeComment(author, postId, "댓글", null);
        assertThat(reportComment(commentId, reporter, BODY)).hasStatus(HttpStatus.CREATED);

        // when
        MvcTestResult again = reportComment(commentId, reporter, BODY);

        // then
        assertThat(again).hasStatus(HttpStatus.CONFLICT);
        assertThat(again).bodyJson().extractingPath("$.code").isEqualTo("COMMUNITY_ALREADY_REPORTED");
        assertThat(reportRows("COMMENT", commentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-29][CM-07] 달린 글이 ACTIVE가 아니면 댓글 신고는 404 NOT_FOUND이고, 지운 댓글과 없는 댓글도 404다")
    void commentOfHiddenPostIsNotFound() {
        // given
        Cookie author = session();
        Cookie reporter = session();
        long postId = fixture.writePost(author, "제목", "본문", null);
        long commentId = fixture.writeComment(author, postId, "댓글", null);
        long deletedId = fixture.writeComment(author, postId, "지울 댓글", null);
        fixture.send(mvc.delete().uri(COMMENTS + deletedId), author, null);
        jdbc.update("UPDATE community_post SET status = 'HIDDEN' WHERE id = ?", postId);

        // when
        MvcTestResult ofHiddenPost = reportComment(commentId, reporter, BODY);
        jdbc.update("UPDATE community_post SET status = 'ACTIVE' WHERE id = ?", postId);
        MvcTestResult deleted = reportComment(deletedId, reporter, BODY);
        MvcTestResult missing = reportComment(999_999L, reporter, BODY);

        // then
        for (MvcTestResult result : List.of(ofHiddenPost, deleted, missing)) {
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
        assertThat(reportRows("COMMENT", commentId)).isZero();
    }

    private Cookie session() {
        return fixture.verifiedSession(fixture.saveMember(TestSequence.nickname()));
    }

    private List<Cookie> sessions(int count) {
        List<Cookie> sessions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sessions.add(session());
        }
        return sessions;
    }

    private MvcTestResult report(long postId, Cookie session, String body) {
        return fixture.send(mvc.post().uri(POSTS + "/" + postId + "/reports"), session, body);
    }

    private MvcTestResult reportComment(long commentId, Cookie session, String body) {
        return fixture.send(mvc.post().uri(COMMENTS + commentId + "/reports"), session, body);
    }

    private int reportRows(String targetType, long targetId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM community_report WHERE target_type = ? AND target_id = ?",
                Integer.class,
                targetType,
                targetId);
    }
}
