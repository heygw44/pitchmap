package com.pitchmap.trust.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.member.application.MemberProfileService;
import com.pitchmap.trust.domain.CompanionReviewPolicy;
import com.pitchmap.trust.infra.CompanionReviewMapper;
import com.pitchmap.trust.infra.CompanionReviewPendingRow;
import com.pitchmap.trust.infra.CompanionReviewPublicRow;
import com.pitchmap.trust.infra.CompanionReviewReceivedRow;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작성할 동행 후기와 받은 후기를 읽는다. 받은 후기는 블라인드 공개 규칙에 따라 내용을 보여 줄지 정한다.
 *
 * <p>공개 규칙: 받은 후기는 받은 회원이 그 작성자에게 같은 베이스캠프의 후기를 썼거나, 그 베이스캠프의 작성 기한이 지났을 때 보인다.
 */
@Service
@RequiredArgsConstructor
public class CompanionReviewQueryService {

    private final TrustSummaryService trustSummaryService;
    private final MemberProfileService memberProfileService;
    private final CompanionReviewMapper companionReviewMapper;
    private final Clock clock;

    /**
     * 호출하면 memberId인 회원이 작성 기한 안에 후기를 쓸 수 있는 완료 베이스캠프를 마감이 빠른 순서로 돌려준다.
     * 각 베이스캠프의 targets는 아직 후기를 쓰지 않은 다른 ACTIVE 멤버이고, 대상이 남지 않은 베이스캠프는 빠진다.
     * 신뢰 단계가 1 미만이면 TRUST_LEVEL_INSUFFICIENT로 거부한다.
     */
    @Transactional(readOnly = true)
    public List<PendingCompanionReview> pending(long memberId) {
        if (trustSummaryService.detail(memberId).trustLevel() < CompanionReviewPolicy.MIN_TRUST_LEVEL) {
            throw new BusinessException(CommonErrorCode.TRUST_LEVEL_INSUFFICIENT);
        }
        Instant writableSince = CompanionReviewPolicy.revealCutoff(clock.instant());
        // 행이 마감이 빠른 베이스캠프 순서로 정렬돼 있어서, 처음 넣는 순서를 지키는 맵으로 묶으면 그 순서가 그대로 남는다.
        Map<Long, List<CompanionReviewPendingRow>> byBasecamp =
                companionReviewMapper.selectPending(memberId, writableSince).stream()
                        .collect(Collectors.groupingBy(
                                CompanionReviewPendingRow::basecampId, LinkedHashMap::new, Collectors.toList()));
        return byBasecamp.values().stream()
                .map(CompanionReviewQueryService::toPending)
                .toList();
    }

    private static PendingCompanionReview toPending(List<CompanionReviewPendingRow> rows) {
        CompanionReviewPendingRow first = rows.get(0);
        List<PendingCompanionReview.Target> targets = rows.stream()
                .map(row -> new PendingCompanionReview.Target(row.targetId(), row.targetNickname()))
                .toList();
        return new PendingCompanionReview(
                first.basecampId(),
                first.basecampTitle(),
                first.completedAt(),
                CompanionReviewPolicy.deadlineOf(first.completedAt()),
                targets);
    }

    /**
     * 호출하면 memberId인 회원이 받은 후기를 최신순으로 한 페이지 돌려준다. 숨긴 후기는 빠진다.
     * 블라인드 공개 전인 후기는 basecampId만 담은 항목으로 돌려준다.
     */
    @Transactional(readOnly = true)
    public CompanionReviewPage<ReceivedCompanionReview> received(long memberId, int page, int size) {
        Instant now = clock.instant();
        List<CompanionReviewReceivedRow> rows =
                companionReviewMapper.selectReceived(memberId, offsetOf(page, size), size + 1);
        return pageOf(rows, page, size, row -> toReceived(row, now));
    }

    /**
     * 호출하면 targetMemberId인 회원이 받은 후기 중 다른 회원에게 보일 것을 최신순으로 한 페이지 돌려준다.
     * 숨겼거나 블라인드 공개 전인 후기는 빠지고, 작성자는 담지 않는다. 없거나 탈퇴한 회원이면 NOT_FOUND로 거부한다.
     */
    @Transactional(readOnly = true)
    public CompanionReviewPage<PublicCompanionReview> forMember(long targetMemberId, int page, int size) {
        memberProfileService.find(targetMemberId);
        Instant revealCutoff = CompanionReviewPolicy.revealCutoff(clock.instant());
        List<CompanionReviewPublicRow> rows = companionReviewMapper.selectPublicByReviewee(
                targetMemberId, revealCutoff, offsetOf(page, size), size + 1);
        return pageOf(
                rows,
                page,
                size,
                row -> new PublicCompanionReview(
                        CompanionReviewTags.parse(row.tags()), row.comment(), row.createdAt()));
    }

    private static ReceivedCompanionReview toReceived(CompanionReviewReceivedRow row, Instant now) {
        if (!CompanionReviewPolicy.isRevealed(row.reverseWritten(), row.completedAt(), now)) {
            return ReceivedCompanionReview.sealed(row.basecampId());
        }
        return new ReceivedCompanionReview(
                row.basecampId(),
                true,
                row.reviewId(),
                row.basecampTitle(),
                row.reviewerId(),
                row.reviewerNickname(),
                row.rejoinWanted(),
                CompanionReviewTags.parse(row.tags()),
                row.comment(),
                row.createdAt());
    }

    // 다음 페이지가 있는지 알려고 한 행을 더 읽고, 그 행은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
    private static <R, T> CompanionReviewPage<T> pageOf(List<R> rows, int page, int size, Function<R, T> mapper) {
        boolean hasNext = rows.size() > size;
        List<T> content = rows.stream().limit(size).map(mapper).toList();
        return new CompanionReviewPage<>(content, page, size, hasNext);
    }

    private static long offsetOf(int page, int size) {
        return (long) page * size;
    }
}
