package com.pitchmap.admin.api;

import static com.pitchmap.common.testsupport.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.community.application.CommunityReportCommand;
import com.pitchmap.community.application.CommunityReportService;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.application.CompanionReviewFixture;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
class AdminCommunityReviewApiIntegrationTest {

    private static final String POSTS = "/api/admin/community/posts";
    private static final String COMMENTS = "/api/admin/community/comments";
    private static final String PASSWORD = "Valid-pass1";
    private static final String SESSION_COOKIE = "SESSION";
    private static final String SECRET_CONTENT = "비밀신고내용이들어간문장";
    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CommunityReportService communityReportService;

    private CompanionReviewFixture fixture;
    private long adminId;
    private Cookie adminSession;
    private long authorId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", adminId);
        adminSession = login(adminId);
        authorId = fixture.saveVerifiedMember("글쓴이");
    }

    @Test
    @DisplayName("[F-29][CM-08] 검토 대기 글은 작성자, 본문, 검토 전 신고 수, 사유별 수, 최근 신고 5건(최신순)과 함께 나오고 신고자는 없다")
    void listShowsPendingPostWithReports() {
        // given
        long pending = insertPost("PENDING_REVIEW", BASE);
        insertPostReport(pending, "SPAM", SECRET_CONTENT + "1", BASE.plus(Duration.ofHours(1)), null);
        insertPostReport(pending, "ABUSE", SECRET_CONTENT + "2", BASE.plus(Duration.ofHours(2)), null);
        insertPostReport(pending, "PRIVACY", SECRET_CONTENT + "3", BASE.plus(Duration.ofHours(3)), null);
        insertPostReport(pending, "SPAM", SECRET_CONTENT + "4", BASE.plus(Duration.ofHours(4)), null);
        insertPostReport(pending, "OTHER", SECRET_CONTENT + "5", BASE.plus(Duration.ofHours(5)), null);
        insertPostReport(pending, "SPAM", SECRET_CONTENT + "6", BASE.plus(Duration.ofHours(6)), null);
        insertPostReport(pending, "ILLEGAL_CAMPING", "검토를 마친 신고", BASE, BASE.plus(Duration.ofDays(1)));
        insertPost("ACTIVE", BASE);

        // when
        MvcTestResult result = get(POSTS);

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(ids(result, "$.content[*].postId")).containsExactly(pending);
        assertThat(result).bodyJson().extractingPath("$.content[0].title").isEqualTo("제목");
        assertThat(result).bodyJson().extractingPath("$.content[0].content").isEqualTo("본문 전체");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].author.memberId")
                .isEqualTo((int) authorId);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo("글쓴이");
        assertThat(result).bodyJson().extractingPath("$.content[0].status").isEqualTo("PENDING_REVIEW");
        assertThat(result).bodyJson().extractingPath("$.content[0].reportCount").isEqualTo(6);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.SPAM")
                .isEqualTo(3);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.ABUSE")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.PRIVACY")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.OTHER")
                .isEqualTo(1);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.ILLEGAL_CAMPING")
                .isEqualTo(0);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.MONEY_SCAM")
                .isEqualTo(0);
        List<String> recent = json(result, "$.content[0].recentReports[*].content");
        assertThat(recent)
                .containsExactly(
                        SECRET_CONTENT + "6",
                        SECRET_CONTENT + "5",
                        SECRET_CONTENT + "4",
                        SECRET_CONTENT + "3",
                        SECRET_CONTENT + "2");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.content[0].recentReports[0].reason")
                .isEqualTo("SPAM");
        assertThat(result).bodyJson().doesNotHavePath("$.content[0].recentReports[0].reporterId");
        assertThat(result).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-29][CM-08] 금전 요구·사기 신고가 있는 글이 먼저 나오고, 같은 묶음에서는 상태가 바뀐 시각이 이른 글부터 나오며, 페이지를 넘기면 hasNext를 알린다")
    void listPutsMoneyScamFirstThenOldest() {
        // given
        long plainOld = insertPost("PENDING_REVIEW", BASE);
        long scamNew = insertPost("PENDING_REVIEW", BASE.plus(Duration.ofHours(5)));
        long plainNew = insertPost("PENDING_REVIEW", BASE.plus(Duration.ofHours(1)));
        long scamOld = insertPost("PENDING_REVIEW", BASE.plus(Duration.ofHours(3)));
        insertPostReport(plainOld, "SPAM", null, BASE, null);
        insertPostReport(scamNew, "MONEY_SCAM", null, BASE, null);
        insertPostReport(scamOld, "MONEY_SCAM", null, BASE, null);
        insertPostReport(scamOld, "SPAM", null, BASE, null);

        // when
        MvcTestResult all = get(POSTS);
        MvcTestResult first = get(POSTS + "?size=3");
        MvcTestResult second = get(POSTS + "?size=3&page=1");

        // then
        assertThat(ids(all, "$.content[*].postId")).containsExactly(scamOld, scamNew, plainOld, plainNew);
        assertThat(ids(first, "$.content[*].postId")).containsExactly(scamOld, scamNew, plainOld);
        assertThat(first).bodyJson().extractingPath("$.hasNext").isEqualTo(true);
        assertThat(ids(second, "$.content[*].postId")).containsExactly(plainNew);
        assertThat(second).bodyJson().extractingPath("$.hasNext").isEqualTo(false);
    }

    @Test
    @DisplayName(
            "[F-29][CM-08] status=HIDDEN이면 숨긴 글만 나오고 신고가 없는 글도 보이며, 허용하지 않는 status와 범위를 벗어난 size는 400 INVALID_INPUT이다")
    void listFiltersByStatusAndValidatesInput() {
        // given
        long hidden = insertPost("HIDDEN", BASE);
        insertPost("PENDING_REVIEW", BASE);

        // when // then
        assertThat(ids(get(POSTS + "?status=HIDDEN"), "$.content[*].postId")).containsExactly(hidden);
        assertThat(get(POSTS + "?status=HIDDEN"))
                .bodyJson()
                .extractingPath("$.content[0].reportCount")
                .isEqualTo(0);
        for (String query : List.of("?status=ACTIVE", "?status=DELETED", "?status=hidden", "?size=51", "?size=0")) {
            MvcTestResult result = get(POSTS + query);
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_INPUT");
        }
    }

    @Test
    @DisplayName("[F-29][CM-08] 검토 대기 글을 숨기면 HIDDEN이 되어 상세가 404이고 감사 로그 한 줄이 남으며 detail에 글자가 없다")
    void hidePostWritesAuditLog() {
        // given
        long postId = insertPost("PENDING_REVIEW", BASE);
        insertPostReport(postId, "SPAM", SECRET_CONTENT, BASE, null);

        // when
        MvcTestResult result = post(POSTS + "/" + postId + "/hide");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().isStrictlyEqualTo("{\"postId\":" + postId + ",\"status\":\"HIDDEN\"}");
        assertThat(postStatus(postId)).isEqualTo("HIDDEN");
        assertThat(mvc.get().uri("/api/community/posts/" + postId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(ids(get(POSTS + "?status=HIDDEN"), "$.content[*].postId")).containsExactly(postId);
        assertThat(count("admin_audit_log WHERE action = 'COMMUNITY_HIDE'")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(admin_id, ':', target_type, ':', target_id) FROM admin_audit_log"
                                + " WHERE action = 'COMMUNITY_HIDE'",
                        String.class))
                .isEqualTo(adminId + ":COMMUNITY_POST:" + postId);
        String detail =
                jdbc.queryForObject("SELECT detail FROM admin_audit_log WHERE action = 'COMMUNITY_HIDE'", String.class);
        assertThat((String) JsonPath.read(detail, "$.fromStatus")).isEqualTo("PENDING_REVIEW");
        assertThat((String) JsonPath.read(detail, "$.toStatus")).isEqualTo("HIDDEN");
        assertThat(detail)
                .doesNotContain(SECRET_CONTENT)
                .doesNotContain("본문 전체")
                .doesNotContain("제목");
    }

    @Test
    @DisplayName("[F-29][CM-08] 신고가 없는 ACTIVE 글도 바로 숨길 수 있다")
    void hideActivePostWithoutReports() {
        // given
        long postId = insertPost("ACTIVE", BASE);

        // when
        MvcTestResult result = post(POSTS + "/" + postId + "/hide");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(postStatus(postId)).isEqualTo("HIDDEN");
    }

    @Test
    @DisplayName("[F-29][CM-08] 검토 대기 글을 복구하면 ACTIVE가 되어 상세가 200이고 검토 전 신고가 검토 완료로 표시되며 감사 로그에 건수가 남는다")
    void restorePostMarksReportsReviewed() {
        // given
        long postId = insertPost("PENDING_REVIEW", BASE);
        for (int i = 0; i < 5; i++) {
            insertPostReport(postId, "SPAM", SECRET_CONTENT, BASE.plus(Duration.ofMinutes(i)), null);
        }
        insertPostReport(postId, "ABUSE", "이전에 검토한 신고", BASE, BASE.plus(Duration.ofHours(1)));

        // when
        MvcTestResult result = post(POSTS + "/" + postId + "/restore");

        // then
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(postStatus(postId)).isEqualTo("ACTIVE");
        assertThat(mvc.get().uri("/api/community/posts/" + postId).exchange()).hasStatus(HttpStatus.OK);
        assertThat(count("community_report WHERE target_type = 'POST' AND target_id = " + postId))
                .isEqualTo(6);
        assertThat(count("community_report WHERE target_id = " + postId + " AND reviewed_at IS NULL"))
                .isZero();
        String detail = jdbc.queryForObject(
                "SELECT detail FROM admin_audit_log WHERE action = 'COMMUNITY_RESTORE'", String.class);
        assertThat((String) JsonPath.read(detail, "$.fromStatus")).isEqualTo("PENDING_REVIEW");
        assertThat((String) JsonPath.read(detail, "$.toStatus")).isEqualTo("ACTIVE");
        assertThat((Integer) JsonPath.read(detail, "$.reviewedReportCount")).isEqualTo(5);
        assertThat(detail).doesNotContain(SECRET_CONTENT);
    }

    @Test
    @DisplayName("[F-29][CM-07][CM-08] 복구한 글은 새 신고 1건으로는 검토 대기가 되지 않고 새 신고 5건이면 되며, 이미 신고한 회원은 다시 신고할 수 없다")
    void afterRestoreOnlyNewReportsCount() {
        // given
        long postId = insertPost("PENDING_REVIEW", BASE);
        long firstReporter = insertPostReport(postId, "SPAM", null, BASE, null);
        for (int i = 1; i < 5; i++) {
            insertPostReport(postId, "SPAM", null, BASE.plus(Duration.ofMinutes(i)), null);
        }
        assertThat(post(POSTS + "/" + postId + "/restore")).hasStatus(HttpStatus.OK);
        CommunityReportCommand report = new CommunityReportCommand("SPAM", "다시 확인했다");

        // when
        communityReportService.reportPost(newMemberId(), postId, report);

        // then
        assertThat(postStatus(postId)).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> communityReportService.reportPost(firstReporter, postId, report))
                .isInstanceOf(BusinessException.class);

        // when
        for (int i = 0; i < 4; i++) {
            communityReportService.reportPost(newMemberId(), postId, report);
        }

        // then
        assertThat(postStatus(postId)).isEqualTo("PENDING_REVIEW");
        assertThat(count("community_report WHERE target_id = " + postId + " AND reviewed_at IS NULL"))
                .isEqualTo(5);
        assertThat(get(POSTS))
                .bodyJson()
                .extractingPath("$.content[0].reportCount")
                .isEqualTo(5);
    }

    @Test
    @DisplayName(
            "[F-29][CM-08] 없는 글과 작성자가 지운 글은 404 NOT_FOUND이고, 이미 숨긴 글 숨김과 ACTIVE 글 복구는 409 COMMUNITY_INVALID_STATE이며 감사 로그가 남지 않는다")
    void notFoundAndInvalidState() {
        // given
        long deleted = insertPost("DELETED", BASE);
        long hidden = insertPost("HIDDEN", BASE);
        long active = insertPost("ACTIVE", BASE);

        // when
        MvcTestResult missing = post(POSTS + "/999999/hide");
        MvcTestResult deletedHide = post(POSTS + "/" + deleted + "/hide");
        MvcTestResult deletedRestore = post(POSTS + "/" + deleted + "/restore");
        MvcTestResult hideHidden = post(POSTS + "/" + hidden + "/hide");
        MvcTestResult restoreActive = post(POSTS + "/" + active + "/restore");

        // then
        for (MvcTestResult result : List.of(missing, deletedHide, deletedRestore)) {
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
        for (MvcTestResult result : List.of(hideHidden, restoreActive)) {
            assertThat(result).hasStatus(HttpStatus.CONFLICT);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("COMMUNITY_INVALID_STATE");
        }
        assertThat(postStatus(deleted)).isEqualTo("DELETED");
        assertThat(count("admin_audit_log")).isZero();
    }

    @Test
    @DisplayName("[F-29][CM-08] 댓글 검토 목록은 글 ID와 작성자, 원문, 사유별 신고 수를 담고 지운 댓글이 아닌 숨긴 댓글도 원문을 준다")
    void commentListShowsReports() {
        // given
        long postId = insertPost("ACTIVE", BASE);
        long pending = insertComment(postId, "PENDING_REVIEW", "신고된 댓글", BASE);
        long hidden = insertComment(postId, "HIDDEN", "숨긴 댓글", BASE.plus(Duration.ofHours(1)));
        insertComment(postId, "ACTIVE", "멀쩡한 댓글", BASE);
        for (int i = 0; i < 5; i++) {
            insertCommentReport(
                    pending, i == 0 ? "MONEY_SCAM" : "ABUSE", SECRET_CONTENT + i, BASE.plus(Duration.ofMinutes(i)));
        }

        // when
        MvcTestResult pendingList = get(COMMENTS);
        MvcTestResult hiddenList = get(COMMENTS + "?status=HIDDEN");

        // then
        assertThat(pendingList).hasStatus(HttpStatus.OK);
        assertThat(ids(pendingList, "$.content[*].commentId")).containsExactly(pending);
        assertThat(pendingList).bodyJson().extractingPath("$.content[0].postId").isEqualTo((int) postId);
        assertThat(pendingList)
                .bodyJson()
                .extractingPath("$.content[0].content")
                .isEqualTo("신고된 댓글");
        assertThat(pendingList)
                .bodyJson()
                .extractingPath("$.content[0].author.nickname")
                .isEqualTo("글쓴이");
        assertThat(pendingList)
                .bodyJson()
                .extractingPath("$.content[0].reportCount")
                .isEqualTo(5);
        assertThat(pendingList)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.MONEY_SCAM")
                .isEqualTo(1);
        assertThat(pendingList)
                .bodyJson()
                .extractingPath("$.content[0].reasonCounts.ABUSE")
                .isEqualTo(4);
        assertThat(pendingList)
                .bodyJson()
                .extractingPath("$.content[0].recentReports")
                .asList()
                .hasSize(5);
        assertThat(pendingList).bodyJson().doesNotHavePath("$.content[0].recentReports[0].reporterId");
        assertThat(ids(hiddenList, "$.content[*].commentId")).containsExactly(hidden);
        assertThat(hiddenList).bodyJson().extractingPath("$.content[0].content").isEqualTo("숨긴 댓글");
        assertThat(hiddenList)
                .bodyJson()
                .extractingPath("$.content[0].reportCount")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("[F-29][CM-08] 댓글을 숨기면 내용은 그대로이고 복구하면 ACTIVE로 돌아오며 검토 전 신고가 검토 완료로 표시되고 감사 로그가 남는다")
    void hideAndRestoreComment() {
        // given
        long postId = insertPost("ACTIVE", BASE);
        long commentId = insertComment(postId, "PENDING_REVIEW", "신고된 댓글", BASE);
        for (int i = 0; i < 5; i++) {
            insertCommentReport(commentId, "SPAM", null, BASE.plus(Duration.ofMinutes(i)));
        }

        // when
        MvcTestResult hide = post(COMMENTS + "/" + commentId + "/hide");

        // then
        assertThat(hide).hasStatus(HttpStatus.OK);
        assertThat(hide).bodyJson().isStrictlyEqualTo("{\"commentId\":" + commentId + ",\"status\":\"HIDDEN\"}");
        assertThat(commentStatus(commentId)).isEqualTo("HIDDEN");
        assertThat(jdbc.queryForObject("SELECT content FROM community_comment WHERE id = ?", String.class, commentId))
                .isEqualTo("신고된 댓글");

        // when
        MvcTestResult restore = post(COMMENTS + "/" + commentId + "/restore");

        // then
        assertThat(restore).hasStatus(HttpStatus.OK);
        assertThat(restore).bodyJson().isStrictlyEqualTo("{\"commentId\":" + commentId + ",\"status\":\"ACTIVE\"}");
        assertThat(commentStatus(commentId)).isEqualTo("ACTIVE");
        assertThat(count("community_report WHERE target_type = 'COMMENT' AND target_id = " + commentId
                        + " AND reviewed_at IS NULL"))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(target_type, ':', target_id) FROM admin_audit_log"
                                + " WHERE action = 'COMMUNITY_RESTORE'",
                        String.class))
                .isEqualTo("COMMUNITY_COMMENT:" + commentId);
        String detail = jdbc.queryForObject(
                "SELECT detail FROM admin_audit_log WHERE action = 'COMMUNITY_RESTORE'", String.class);
        assertThat((String) JsonPath.read(detail, "$.fromStatus")).isEqualTo("HIDDEN");
        assertThat((Integer) JsonPath.read(detail, "$.reviewedReportCount")).isEqualTo(5);
        assertThat(count("admin_audit_log WHERE action = 'COMMUNITY_HIDE'")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-29][CM-08] 달린 글이 숨겨져 있어도 댓글 숨김은 되고, 없는 댓글과 지운 댓글은 404, 이미 숨긴 댓글 숨김은 409다")
    void commentModerationEdgeCases() {
        // given
        long hiddenPost = insertPost("HIDDEN", BASE);
        long onHiddenPost = insertComment(hiddenPost, "ACTIVE", "댓글", BASE);
        long deleted = insertComment(hiddenPost, "DELETED", null, BASE);
        long hidden = insertComment(hiddenPost, "HIDDEN", "숨긴 댓글", BASE);

        // when
        MvcTestResult ok = post(COMMENTS + "/" + onHiddenPost + "/hide");
        MvcTestResult missing = post(COMMENTS + "/999999/hide");
        MvcTestResult deletedRestore = post(COMMENTS + "/" + deleted + "/restore");
        MvcTestResult hideHidden = post(COMMENTS + "/" + hidden + "/hide");

        // then
        assertThat(ok).hasStatus(HttpStatus.OK);
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(deletedRestore).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(hideHidden).hasStatus(HttpStatus.CONFLICT);
        assertThat(hideHidden).bodyJson().extractingPath("$.code").isEqualTo("COMMUNITY_INVALID_STATE");
        assertThat(count("admin_audit_log")).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-29][CM-08] 관리자가 아닌 회원은 목록, 숨김, 복구에서 403 ACCESS_DENIED이고 로그인하지 않으면 401이다")
    void requiresAdmin() {
        // given
        long postId = insertPost("PENDING_REVIEW", BASE);
        Cookie user = login(fixture.saveVerifiedMember(TestSequence.nickname()));

        // when
        MvcTestResult anonymous = mvc.get().uri(POSTS).exchange();
        MvcTestResult list = mvc.get().uri(POSTS).cookie(user).exchange();
        MvcTestResult comments = mvc.get().uri(COMMENTS).cookie(user).exchange();
        MvcTestResult hide = mvc.post()
                .uri(POSTS + "/" + postId + "/hide")
                .cookie(user)
                .with(csrf())
                .exchange();
        MvcTestResult restore = mvc.post()
                .uri(COMMENTS + "/" + postId + "/restore")
                .cookie(user)
                .with(csrf())
                .exchange();

        // then
        assertThat(anonymous).hasStatus(HttpStatus.UNAUTHORIZED);
        for (MvcTestResult result : List.of(list, comments, hide, restore)) {
            assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCESS_DENIED");
        }
        assertThat(postStatus(postId)).isEqualTo("PENDING_REVIEW");
    }

    private long insertPost(String status, Instant updatedAt) {
        jdbc.update(
                "INSERT INTO community_post (member_id, title, content, status, created_at, updated_at)"
                        + " VALUES (?, '제목', '본문 전체', ?, ?, ?)",
                authorId,
                status,
                BASE,
                updatedAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM community_post", Long.class);
    }

    private long insertComment(long postId, String status, String content, Instant updatedAt) {
        jdbc.update(
                "INSERT INTO community_comment (post_id, member_id, content, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                postId,
                authorId,
                content,
                status,
                BASE,
                updatedAt);
        return jdbc.queryForObject("SELECT MAX(id) FROM community_comment", Long.class);
    }

    private long insertPostReport(long postId, String reason, String content, Instant createdAt, Instant reviewedAt) {
        return insertReport("POST", postId, reason, content, createdAt, reviewedAt);
    }

    private long insertCommentReport(long commentId, String reason, String content, Instant createdAt) {
        return insertReport("COMMENT", commentId, reason, content, createdAt, null);
    }

    private long insertReport(
            String targetType, long targetId, String reason, String content, Instant createdAt, Instant reviewedAt) {
        long reporterId = newMemberId();
        jdbc.update(
                "INSERT INTO community_report (target_type, target_id, reporter_id, reason, content, created_at, reviewed_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                targetType,
                targetId,
                reporterId,
                reason,
                content,
                createdAt,
                reviewedAt);
        return reporterId;
    }

    private long newMemberId() {
        return fixture.saveVerifiedMember(TestSequence.nickname());
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).cookie(adminSession).exchange();
    }

    private MvcTestResult post(String uri) {
        return mvc.post().uri(uri).cookie(adminSession).with(csrf()).exchange();
    }

    private static <T> T json(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }

    private static List<Long> ids(MvcTestResult result, String path) {
        List<Number> numbers = json(result, path);
        return numbers.stream().map(Number::longValue).toList();
    }

    private String postStatus(long postId) {
        return jdbc.queryForObject("SELECT status FROM community_post WHERE id = ?", String.class, postId);
    }

    private String commentStatus(long commentId) {
        return jdbc.queryForObject("SELECT status FROM community_comment WHERE id = ?", String.class, commentId);
    }

    private int count(String tableAndCondition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableAndCondition, Integer.class);
    }

    private Cookie login(long memberId) {
        jdbc.update("UPDATE member SET password_hash = ? WHERE id = ?", passwordEncoder.encode(PASSWORD), memberId);
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
        MvcTestResult result = mvc.post()
                .uri("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        Cookie session = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(session).isNotNull();
        return session;
    }
}
