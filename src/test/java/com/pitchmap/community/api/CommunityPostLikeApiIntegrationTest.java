package com.pitchmap.community.api;

import static com.pitchmap.community.api.CommunityApiFixture.POSTS;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.application.EmailVerificationService;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@AutoConfigureMockMvc
class CommunityPostLikeApiIntegrationTest {

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
    @DisplayName("[F-29][CM-05] 좋아요를 누르고 다시 눌러도 수가 늘지 않고, 취소하고 다시 취소해도 수가 줄지 않으며 같은 응답을 받는다")
    void likeAndUnlikeAreIdempotent() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember("새벽능선"));
        long postId = fixture.writePost(session, "FREE", "제목", "본문", null);

        // when
        MvcTestResult first = fixture.send(mvc.put().uri(likeUri(postId)), session, null);
        MvcTestResult second = fixture.send(mvc.put().uri(likeUri(postId)), session, null);
        MvcTestResult cancel = fixture.send(mvc.delete().uri(likeUri(postId)), session, null);
        MvcTestResult cancelAgain = fixture.send(mvc.delete().uri(likeUri(postId)), session, null);

        // then
        assertThat(first).hasStatus(HttpStatus.OK);
        assertThat(first).bodyJson().isStrictlyEqualTo("{ \"liked\": true, \"likeCount\": 1 }");
        assertThat(second).hasStatus(HttpStatus.OK);
        assertThat(second).bodyJson().isStrictlyEqualTo("{ \"liked\": true, \"likeCount\": 1 }");
        assertThat(cancel).hasStatus(HttpStatus.OK);
        assertThat(cancel).bodyJson().isStrictlyEqualTo("{ \"liked\": false, \"likeCount\": 0 }");
        assertThat(cancelAgain).hasStatus(HttpStatus.OK);
        assertThat(cancelAgain).bodyJson().isStrictlyEqualTo("{ \"liked\": false, \"likeCount\": 0 }");
        assertThat(likeRows(postId)).isZero();
    }

    @Test
    @DisplayName("[F-29][CM-05] 서로 다른 회원이 좋아요를 누르면 수가 회원 수만큼 늘어난다")
    void likesFromDifferentMembersAreCounted() {
        // given
        Cookie first = fixture.verifiedSession(fixture.saveMember("새벽능선"));
        Cookie second = fixture.verifiedSession(fixture.saveMember("아침안개"));
        long postId = fixture.writePost(first, "FREE", "제목", "본문", null);

        // when
        fixture.send(mvc.put().uri(likeUri(postId)), first, null);
        MvcTestResult result = fixture.send(mvc.put().uri(likeUri(postId)), second, null);

        // then
        assertThat(result).bodyJson().isStrictlyEqualTo("{ \"liked\": true, \"likeCount\": 2 }");
        assertThat(likeRows(postId)).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-29][CM-03] 삭제되거나 숨겨진 글에는 좋아요를 누르거나 취소해도 404 NOT_FOUND다")
    void deletedOrHiddenPostIsNotFound() {
        // given
        Cookie session = fixture.verifiedSession(fixture.saveMember("새벽능선"));
        long deletedId = fixture.writePost(session, "FREE", "지운 글", "본문", null);
        long hiddenId = fixture.writePost(session, "FREE", "숨긴 글", "본문", null);
        fixture.send(mvc.delete().uri(POSTS + "/" + deletedId), session, null);
        jdbc.update("UPDATE community_post SET status = 'HIDDEN' WHERE id = ?", hiddenId);

        // when
        MvcTestResult putDeleted = fixture.send(mvc.put().uri(likeUri(deletedId)), session, null);
        MvcTestResult deleteDeleted = fixture.send(mvc.delete().uri(likeUri(deletedId)), session, null);
        MvcTestResult putHidden = fixture.send(mvc.put().uri(likeUri(hiddenId)), session, null);
        MvcTestResult deleteHidden = fixture.send(mvc.delete().uri(likeUri(hiddenId)), session, null);

        // then
        for (MvcTestResult result : new MvcTestResult[] {putDeleted, deleteDeleted, putHidden, deleteHidden}) {
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
        assertThat(likeRows(deletedId)).isZero();
        assertThat(likeRows(hiddenId)).isZero();
    }

    @Test
    @DisplayName("[F-29][CM-05] 목록과 상세에 likeCount가 나오고, likedByMe는 좋아요를 누른 본인에게만 true이며 비로그인 상세에는 없다")
    void likeCountAndLikedByMeInListAndDetail() {
        // given
        Cookie liker = fixture.verifiedSession(fixture.saveMember("새벽능선"));
        Cookie other = fixture.verifiedSession(fixture.saveMember("아침안개"));
        long postId = fixture.writePost(liker, "FREE", "제목", "본문", null);
        fixture.send(mvc.put().uri(likeUri(postId)), liker, null);

        // when
        MvcTestResult list = mvc.get().uri(POSTS).exchange();
        MvcTestResult anonymousDetail = mvc.get().uri(POSTS + "/" + postId).exchange();
        MvcTestResult likerDetail =
                mvc.get().uri(POSTS + "/" + postId).cookie(liker).exchange();
        MvcTestResult otherDetail =
                mvc.get().uri(POSTS + "/" + postId).cookie(other).exchange();

        // then
        assertThat(list).bodyJson().extractingPath("$.content[0].likeCount").isEqualTo(1);
        assertThat(list).bodyJson().doesNotHavePath("$.content[0].likedByMe");
        assertThat(anonymousDetail).bodyJson().extractingPath("$.likeCount").isEqualTo(1);
        assertThat(anonymousDetail).bodyJson().doesNotHavePath("$.likedByMe");
        assertThat(likerDetail).bodyJson().extractingPath("$.likedByMe").isEqualTo(true);
        assertThat(otherDetail).bodyJson().extractingPath("$.likeCount").isEqualTo(1);
        assertThat(otherDetail).bodyJson().extractingPath("$.likedByMe").isEqualTo(false);
    }

    @Test
    @DisplayName("[F-29][CM-05] 이메일 인증을 마치지 않은 회원은 좋아요를 누를 수 없다")
    void unverifiedMemberCannotLike() {
        // given
        Member author = fixture.saveMember("새벽능선");
        long postId = fixture.writePost(fixture.verifiedSession(author), "FREE", "제목", "본문", null);
        Cookie unverified = fixture.loginOnly(fixture.saveMember("아침안개"));

        // when
        MvcTestResult result = fixture.send(mvc.put().uri(likeUri(postId)), unverified, null);

        // then
        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("MEMBER_NOT_VERIFIED");
        assertThat(likeRows(postId)).isZero();
    }

    private static String likeUri(long postId) {
        return POSTS + "/" + postId + "/like";
    }

    private int likeRows(long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM community_post_like WHERE post_id = ?", Integer.class, postId);
    }
}
