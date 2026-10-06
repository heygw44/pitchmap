package com.pitchmap.review.application;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.review.domain.ReviewErrorCode;
import com.pitchmap.spot.application.SpotDetail;
import com.pitchmap.spot.application.SpotDetailQueryService;
import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.ParkAreaJudgement;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.infra.SpotJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SpotReviewServiceIntegrationTest {

    // 테스트 시계의 기본 시각은 한국 날짜로 2026-10-05 낮 12시다.
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Autowired
    private SpotReviewCommandService commandService;

    @Autowired
    private SpotReviewQueryService queryService;

    @Autowired
    private SpotDetailQueryService spotDetailQueryService;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("[F-10] 후기를 쓰면 행이 저장되고, 장소 상세의 rating과 recentReviews에 바로 반영된다")
    void writtenReviewAppearsInDetail() {
        // given
        long spotId = saveSpot();
        long memberId = saveMember("새벽능선");

        // when
        long reviewId = commandService.write(memberId, spotId, write(TODAY.minusDays(1), 4, "물이 가까웠다"));

        // then
        assertThat(rowCount(spotId)).isEqualTo(1);
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);
        assertThat(detail.rating().count()).isEqualTo(1);
        assertThat(detail.rating().average()).isEqualTo(4.0);
        assertThat(detail.recentReviews()).hasSize(1);
        assertThat(detail.recentReviews().get(0).reviewId()).isEqualTo(reviewId);
        assertThat(detail.recentReviews().get(0).authorId()).isEqualTo(memberId);
        assertThat(detail.recentReviews().get(0).authorNickname()).isEqualTo("새벽능선");
        assertThat(detail.recentReviews().get(0).visitedDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(detail.recentReviews().get(0).rating()).isEqualTo(4);
        assertThat(detail.recentReviews().get(0).content()).isEqualTo("물이 가까웠다");
        assertThat(detail.recentReviews().get(0).createdAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    @Test
    @DisplayName("[F-10] 후기가 없는 장소의 rating은 average가 null이고 count가 0이며, recentReviews는 비어 있다")
    void detailWithoutReviewsHasNullAverage() {
        // given
        long spotId = saveSpot();

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.rating().average()).isNull();
        assertThat(detail.rating().count()).isZero();
        assertThat(detail.recentReviews()).isEmpty();
    }

    @Test
    @DisplayName("[F-10] 평점 4, 4, 5의 평균은 소수 첫째 자리에서 반올림한 4.3이다")
    void averageIsRoundedToOneDecimal() {
        // given
        long spotId = saveSpot();
        commandService.write(saveMember(), spotId, write(TODAY, 4, "좋다"));
        commandService.write(saveMember(), spotId, write(TODAY, 4, "좋다"));
        commandService.write(saveMember(), spotId, write(TODAY, 5, "좋다"));

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.rating().count()).isEqualTo(3);
        assertThat(detail.rating().average()).isEqualTo(4.3);
    }

    @Test
    @DisplayName("[F-10] 같은 회원이 같은 장소에 같은 방문일로 다시 쓰면 SPOT_REVIEW_DUPLICATED이고, 방문일이 다르면 쓸 수 있다")
    void sameMemberSameSpotSameVisitedDateIsDuplicated() {
        // given
        long spotId = saveSpot();
        long memberId = saveMember();
        commandService.write(memberId, spotId, write(TODAY, 4, "좋다"));

        // when, then
        assertThatThrownBy(() -> commandService.write(memberId, spotId, write(TODAY, 5, "또 갔다")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.SPOT_REVIEW_DUPLICATED));
        commandService.write(memberId, spotId, write(TODAY.minusDays(1), 5, "어제도 갔다"));
        assertThat(rowCount(spotId)).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-10] 방문일이 오늘이면 쓸 수 있고 내일이면 INVALID_INPUT이며, 오늘은 한국 날짜로 센다")
    void visitedDateCannotBeAfterTodayInKorea() {
        // given
        long spotId = saveSpot();
        long memberId = saveMember();

        // when, then: 한국 날짜 2026-10-05 낮이다.
        commandService.write(memberId, spotId, write(TODAY, 3, "오늘 다녀왔다"));
        assertThatThrownBy(() -> commandService.write(memberId, spotId, write(TODAY.plusDays(1), 3, "내일 갈 곳")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));

        // when, then: UTC로는 아직 10월 5일이지만 한국은 10월 6일 0시 30분이다.
        clock.setInstant(Instant.parse("2026-10-05T15:30:00Z"));
        commandService.write(memberId, spotId, write(TODAY.plusDays(1), 3, "한국 날짜로는 오늘"));
        assertThat(rowCount(spotId)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    @DisplayName("[F-10] 평점이 1~5가 아니면 INVALID_INPUT이고 저장하지 않는다")
    void invalidRatingIsRejected(int rating) {
        // given
        long spotId = saveSpot();
        long memberId = saveMember();

        // when, then
        assertThatThrownBy(() -> commandService.write(memberId, spotId, write(TODAY, rating, "좋다")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThat(rowCount(spotId)).isZero();
    }

    @Test
    @DisplayName("[F-10] 내용이 공백뿐이거나 2001자이면 INVALID_INPUT이다")
    void invalidContentIsRejected() {
        // given
        long spotId = saveSpot();
        long memberId = saveMember();

        // when, then
        assertThatThrownBy(() -> commandService.write(memberId, spotId, write(TODAY, 3, "   ")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> commandService.write(memberId, spotId, write(TODAY, 3, "가".repeat(2001))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThat(rowCount(spotId)).isZero();
    }

    @Test
    @DisplayName("[F-10] 후기를 고치면 평점과 내용이 바뀌고 방문일은 그대로이며, 고친 항목을 돌려주고 평균에 반영된다")
    void reviseChangesRatingAndContent() {
        // given
        long spotId = saveSpot();
        long memberId = saveMember("새벽능선");
        long reviewId = commandService.write(memberId, spotId, write(TODAY.minusDays(2), 2, "별로였다"));
        commandService.write(saveMember(), spotId, write(TODAY, 4, "좋다"));
        clock.advance(Duration.ofHours(1));

        // when
        SpotReviewItem revised =
                commandService.revise(memberId, reviewId, new SpotReviewReviseCommand(5, "다시 가 보니 좋았다"));

        // then
        assertThat(revised.reviewId()).isEqualTo(reviewId);
        assertThat(revised.authorNickname()).isEqualTo("새벽능선");
        assertThat(revised.visitedDate()).isEqualTo(TODAY.minusDays(2));
        assertThat(revised.rating()).isEqualTo(5);
        assertThat(revised.content()).isEqualTo("다시 가 보니 좋았다");
        assertThat(revised.createdAt()).isEqualTo(MutableClock.DEFAULT_INSTANT);
        assertThat(jdbc.queryForObject(
                        "SELECT updated_at > created_at FROM spot_review WHERE id = ?", Boolean.class, reviewId))
                .isTrue();
        assertThat(spotDetailQueryService.findDetail(spotId).rating().average()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("[F-10] 후기를 지우면 행이 사라지고 상세의 후기 수와 평균에서 빠진다")
    void deleteRemovesReviewFromAggregates() {
        // given
        long spotId = saveSpot();
        long memberId = saveMember();
        long reviewId = commandService.write(memberId, spotId, write(TODAY, 1, "별로였다"));
        commandService.write(saveMember(), spotId, write(TODAY, 5, "좋다"));

        // when
        commandService.delete(memberId, reviewId);

        // then
        assertThat(rowCount(spotId)).isEqualTo(1);
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);
        assertThat(detail.rating().count()).isEqualTo(1);
        assertThat(detail.rating().average()).isEqualTo(5.0);
        assertThat(detail.recentReviews()).hasSize(1);
    }

    @Test
    @DisplayName("[F-10] 다른 회원이 후기를 고치거나 지우면 ACCESS_DENIED이고 후기는 그대로다")
    void otherMemberCannotReviseOrDelete() {
        // given
        long spotId = saveSpot();
        long reviewId = commandService.write(saveMember(), spotId, write(TODAY, 4, "좋다"));
        long other = saveMember();

        // when, then
        assertThatThrownBy(() -> commandService.revise(other, reviewId, new SpotReviewReviseCommand(1, "나쁘다")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> commandService.delete(other, reviewId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
        assertThat(rowCount(spotId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rating FROM spot_review WHERE id = ?", Integer.class, reviewId))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("[F-10] 없는 후기를 고치거나 지우면 NOT_FOUND다")
    void missingReviewIsNotFound() {
        // given
        long memberId = saveMember();

        // when, then
        assertThatThrownBy(() -> commandService.revise(memberId, 999_999L, new SpotReviewReviseCommand(3, "좋다")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> commandService.delete(memberId, 999_999L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "PENDING_REVIEW", "DELETED"})
    @DisplayName("[F-10] ACTIVE가 아닌 장소에 후기를 쓰거나 목록을 읽으면 NOT_FOUND이고, 없는 장소도 같다")
    void inactiveOrMissingSpotIsNotFound(String status) {
        // given
        long spotId = saveSpot();
        long memberId = saveMember();
        jdbc.update("UPDATE spot SET status = ? WHERE id = ?", status, spotId);

        // when, then
        assertThatThrownBy(() -> commandService.write(memberId, spotId, write(TODAY, 4, "좋다")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> queryService.list(spotId, 0, 20))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> commandService.write(memberId, 999_999L, write(TODAY, 4, "좋다")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("[F-10] 장소가 숨겨진 뒤에도 작성자는 자기 후기를 고치고 지울 수 있다")
    void authorCanReviseAndDeleteAfterSpotIsHidden() {
        // given
        long spotId = saveSpot();
        long memberId = saveMember();
        long reviewId = commandService.write(memberId, spotId, write(TODAY, 4, "좋다"));
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", spotId);

        // when
        SpotReviewItem revised = commandService.revise(memberId, reviewId, new SpotReviewReviseCommand(3, "그저 그랬다"));
        commandService.delete(memberId, reviewId);

        // then
        assertThat(revised.rating()).isEqualTo(3);
        assertThat(rowCount(spotId)).isZero();
    }

    @Test
    @DisplayName("[F-10] 목록은 작성 시각이 늦은 순서이고, 작성 시각이 같으면 후기 ID가 큰 것이 먼저이며, hasNext로 다음 페이지를 알린다")
    void listIsNewestFirstWithPaging() {
        // given
        long spotId = saveSpot();
        long first = commandService.write(saveMember(), spotId, write(TODAY, 1, "첫째"));
        clock.advance(Duration.ofMinutes(1));
        long second = commandService.write(saveMember(), spotId, write(TODAY, 2, "둘째"));
        long third = commandService.write(saveMember(), spotId, write(TODAY, 3, "셋째, 둘째와 같은 시각"));

        // when
        SpotReviewPage firstPage = queryService.list(spotId, 0, 2);
        SpotReviewPage secondPage = queryService.list(spotId, 1, 2);

        // then
        assertThat(ids(firstPage)).containsExactly(third, second);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(ids(secondPage)).containsExactly(first);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("[F-10] 후기가 3개를 넘으면 상세의 recentReviews에는 가장 최근 3개만 최신순으로 담긴다")
    void detailShowsOnlyThreeMostRecent() {
        // given
        long spotId = saveSpot();
        long first = writeAt(spotId, 1, Duration.ZERO);
        long second = writeAt(spotId, 2, Duration.ofMinutes(1));
        long third = writeAt(spotId, 3, Duration.ofMinutes(1));
        long fourth = writeAt(spotId, 4, Duration.ofMinutes(1));

        // when
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(detail.recentReviews())
                .extracting(review -> review.reviewId())
                .containsExactly(fourth, third, second)
                .doesNotContain(first);
        assertThat(detail.rating().count()).isEqualTo(4);
        assertThat(detail.rating().average()).isEqualTo(2.5);
    }

    @Test
    @DisplayName("[F-10] 탈퇴해서 닉네임이 익명으로 바뀐 회원의 후기는 목록과 상세에 익명 닉네임으로 나온다")
    void withdrawnAuthorShowsAnonymousNickname() {
        // given
        long spotId = saveSpot();
        long memberId = saveMember("새벽능선");
        commandService.write(memberId, spotId, write(TODAY, 4, "좋다"));
        jdbc.update("UPDATE member SET nickname = ? WHERE id = ?", "탈퇴회원_" + memberId, memberId);

        // when
        SpotReviewPage page = queryService.list(spotId, 0, 20);
        SpotDetail detail = spotDetailQueryService.findDetail(spotId);

        // then
        assertThat(page.content().get(0).authorNickname()).isEqualTo("탈퇴회원_" + memberId);
        assertThat(detail.recentReviews().get(0).authorNickname()).isEqualTo("탈퇴회원_" + memberId);
    }

    private long writeAt(long spotId, int rating, Duration advance) {
        clock.advance(advance);
        return commandService.write(saveMember(), spotId, write(TODAY, rating, "평점 " + rating));
    }

    private static List<Long> ids(SpotReviewPage page) {
        return page.content().stream().map(SpotReviewItem::reviewId).toList();
    }

    private static SpotReviewWriteCommand write(LocalDate visitedDate, int rating, String content) {
        return new SpotReviewWriteCommand(visitedDate, rating, content);
    }

    private long saveSpot() {
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }

    private long saveMember() {
        return memberRepository.saveAndFlush(aMember().build()).getId();
    }

    private long saveMember(String nickname) {
        return memberRepository
                .saveAndFlush(aMember().nickname(nickname).build())
                .getId();
    }

    private int rowCount(long spotId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM spot_review WHERE spot_id = ?", Integer.class, spotId);
    }
}
