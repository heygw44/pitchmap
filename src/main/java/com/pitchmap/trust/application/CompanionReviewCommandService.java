package com.pitchmap.trust.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.trust.domain.CompanionReview;
import com.pitchmap.trust.domain.CompanionReviewPolicy;
import com.pitchmap.trust.domain.CompanionReviewRepository;
import com.pitchmap.trust.domain.TrustErrorCode;
import com.pitchmap.trust.infra.CompanionReviewMapper;
import com.pitchmap.trust.infra.CompanionReviewWriteContextRow;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완료된 베이스캠프의 멤버가 같은 베이스캠프의 다른 멤버에게 동행 후기를 쓴다.
 *
 * <p>후기를 쓸 자격은 따로 저장하지 않고, 쓸 때마다 베이스캠프의 상태와 멤버 목록을 읽어 판단한다. 후기는 쓴 뒤에 고치거나 지울 수 없다.
 * 서비스는 이미 썼는지 먼저 조회해서 거르고, 동시에 들어와 조회를 통과한 요청은 저장할 때 DB의 유니크 제약이 하나만 받는다.
 */
@Service
@RequiredArgsConstructor
public class CompanionReviewCommandService {

    private static final String COMPLETED = "COMPLETED";

    private final TrustSummaryService trustSummaryService;
    private final CompanionReviewRepository companionReviewRepository;
    private final CompanionReviewMapper companionReviewMapper;
    private final Clock clock;

    /**
     * 호출하면 reviewerId인 회원이 basecampId인 베이스캠프에서 command의 상대에게 쓴 후기를 저장하고 후기 ID를 돌려준다.
     *
     * <p>다음 순서로 검사하고 처음 걸린 이유로 거부한다.
     * <ol>
     *   <li>신뢰 단계가 1 미만이면 TRUST_LEVEL_INSUFFICIENT
     *   <li>베이스캠프가 없으면 NOT_FOUND
     *   <li>완료되지 않았거나, 작성자나 상대가 ACTIVE 멤버가 아니거나, 자기 자신에게 쓰면 COMPANION_REVIEW_NOT_ELIGIBLE
     *   <li>작성 기한이 지났으면 COMPANION_REVIEW_DEADLINE_PASSED
     *   <li>같은 상대에게 이미 썼으면 COMPANION_REVIEW_DUPLICATED
     * </ol>
     *
     * 코멘트가 300자를 넘으면 INVALID_INPUT으로 거부한다.
     */
    @Transactional
    public long write(long reviewerId, long basecampId, CompanionReviewWriteCommand command) {
        if (trustSummaryService.detail(reviewerId).trustLevel() < CompanionReviewPolicy.MIN_TRUST_LEVEL) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
        CompanionReviewWriteContextRow context = companionReviewMapper
                .selectWriteContext(basecampId, reviewerId, command.revieweeId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!isEligible(context, reviewerId, command.revieweeId())) {
            throw new BusinessException(TrustErrorCode.COMPANION_REVIEW_NOT_ELIGIBLE);
        }
        Instant now = clock.instant();
        if (!CompanionReviewPolicy.isWritable(context.completedAt(), now)) {
            throw new BusinessException(TrustErrorCode.COMPANION_REVIEW_DEADLINE_PASSED);
        }
        if (context.alreadyWritten()) {
            throw new BusinessException(TrustErrorCode.COMPANION_REVIEW_DUPLICATED);
        }
        return save(newReview(basecampId, reviewerId, command, now)).getId();
    }

    private static boolean isEligible(CompanionReviewWriteContextRow context, long reviewerId, long revieweeId) {
        return COMPLETED.equals(context.basecampStatus())
                && context.completedAt() != null
                && context.reviewerActive()
                && context.revieweeActive()
                && reviewerId != revieweeId;
    }

    private static CompanionReview newReview(
            long basecampId, long reviewerId, CompanionReviewWriteCommand command, Instant now) {
        if (command.comment() != null && command.comment().length() > CompanionReview.COMMENT_MAX_LENGTH) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT, "코멘트는 " + CompanionReview.COMMENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return CompanionReview.write(
                basecampId,
                reviewerId,
                command.revieweeId(),
                command.rejoinWanted(),
                command.tags(),
                command.comment(),
                now);
    }

    private CompanionReview save(CompanionReview review) {
        try {
            return companionReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw CompanionReviewUniqueConstraintTranslator.translate(e);
        }
    }
}
