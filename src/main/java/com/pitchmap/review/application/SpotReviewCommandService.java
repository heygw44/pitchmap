package com.pitchmap.review.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.review.domain.ReviewErrorCode;
import com.pitchmap.review.domain.SpotReview;
import com.pitchmap.review.domain.SpotReviewRepository;
import com.pitchmap.review.infra.SpotReviewMapper;
import com.pitchmap.spot.application.ActiveSpotChecker;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 장소 후기를 쓰고, 자기 후기를 고치거나 지운다.
 *
 * <p>후기를 쓸 수 있는 장소는 지도에 보이는(ACTIVE) 장소뿐이다. 장소가 없거나 ACTIVE가 아니면 서비스는 NOT_FOUND로 거부한다. 반대로 고치거나
 * 지울 때는 장소 상태를 보지 않는다. 장소가 숨겨진 뒤에도 작성자는 자기 후기를 고치거나 지울 수 있어야 하기 때문이다.
 *
 * <p>한 회원은 같은 장소에 방문일마다 후기를 하나만 쓴다. 서비스는 조회로 미리 거르지 않고, 저장할 때 DB가 던지는 유니크 제약 위반을
 * SPOT_REVIEW_DUPLICATED로 바꾼다. 같은 회원의 요청 두 개가 동시에 들어와도 DB가 하나만 받기 때문이다. 평균 평점과 후기 수는 저장하지 않고
 * 장소 상세가 조회할 때 계산하므로, 후기를 쓰고 고치고 지울 때 갱신할 집계가 없고 락도 필요 없다.
 */
@Service
@RequiredArgsConstructor
public class SpotReviewCommandService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final ActiveSpotChecker activeSpotChecker;
    private final SpotReviewRepository spotReviewRepository;
    private final SpotReviewMapper spotReviewMapper;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원의 후기를 저장하고 후기 ID를 돌려준다.
     *
     * <p>방문일이 오늘(한국 날짜)보다 뒤이거나, 평점이 1~5가 아니거나, 내용이 공백뿐이거나 2,000자를 넘으면 INVALID_INPUT으로 거부한다.
     * 같은 장소에 같은 방문일로 쓴 후기가 이미 있으면 SPOT_REVIEW_DUPLICATED로 거부한다.
     */
    @Transactional
    public long write(long memberId, long spotId, SpotReviewWriteCommand command) {
        activeSpotChecker.requireActive(spotId);
        Instant now = clock.instant();
        requireNotFuture(command.visitedDate(), now);
        requireValidBody(command.rating(), command.content());
        SpotReview review =
                SpotReview.write(spotId, memberId, command.visitedDate(), command.rating(), command.content(), now);
        return save(review).getId();
    }

    /**
     * 호출하면 작성자 memberId가 reviewId인 후기의 평점과 내용을 고치고, 고친 뒤의 후기를 돌려준다. 방문일은 그대로다.
     *
     * <p>후기가 없으면 NOT_FOUND로, 다른 회원이 쓴 후기이면 ACCESS_DENIED로 거부한다. 평점이나 내용이 규칙을 어기면 INVALID_INPUT으로 거부한다.
     */
    @Transactional
    public SpotReviewItem revise(long memberId, long reviewId, SpotReviewReviseCommand command) {
        SpotReview review = loadOwnReview(memberId, reviewId);
        requireValidBody(command.rating(), command.content());
        review.revise(command.rating(), command.content(), clock.instant());
        // 고친 내용을 MyBatis로 읽기 전에 DB에 반영한다.
        spotReviewRepository.flush();
        return spotReviewMapper
                .selectById(reviewId)
                .map(SpotReviewItem::from)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 호출하면 작성자 memberId의 후기를 지운다. 후기가 없으면 NOT_FOUND로, 다른 회원이 쓴 후기이면 ACCESS_DENIED로 거부한다. */
    @Transactional
    public void delete(long memberId, long reviewId) {
        spotReviewRepository.delete(loadOwnReview(memberId, reviewId));
    }

    private SpotReview loadOwnReview(long memberId, long reviewId) {
        SpotReview review = spotReviewRepository
                .findById(reviewId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!review.isWrittenBy(memberId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return review;
    }

    private SpotReview save(SpotReview review) {
        try {
            return spotReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ReviewErrorCode.SPOT_REVIEW_DUPLICATED);
        }
    }

    // 방문일은 한국 날짜로 받으므로, 오늘도 한국 날짜로 계산해서 비교한다. 오늘 방문한 장소의 후기는 쓸 수 있다.
    private static void requireNotFuture(LocalDate visitedDate, Instant now) {
        if (visitedDate == null) {
            throw invalidInput("방문일을 입력해야 합니다.");
        }
        if (visitedDate.isAfter(LocalDate.ofInstant(now, KOREA))) {
            throw invalidInput("방문일은 오늘(한국 날짜)보다 뒤일 수 없습니다.");
        }
    }

    private static void requireValidBody(int rating, String content) {
        if (rating < SpotReview.MIN_RATING || rating > SpotReview.MAX_RATING) {
            throw invalidInput("평점은 " + SpotReview.MIN_RATING + "~" + SpotReview.MAX_RATING + "이어야 합니다.");
        }
        if (content == null || content.isBlank()) {
            throw invalidInput("후기 내용을 입력해야 합니다.");
        }
        if (content.length() > SpotReview.CONTENT_MAX_LENGTH) {
            throw invalidInput("후기 내용은 " + SpotReview.CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(CommonErrorCode.INVALID_INPUT, message);
    }
}
