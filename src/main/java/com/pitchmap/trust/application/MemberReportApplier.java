package com.pitchmap.trust.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.member.application.MemberSuspensionService;
import com.pitchmap.trust.domain.CompanionReviewPolicy;
import com.pitchmap.trust.domain.MemberReport;
import com.pitchmap.trust.domain.MemberReportRepository;
import com.pitchmap.trust.domain.ReportKind;
import com.pitchmap.trust.domain.Sanction;
import com.pitchmap.trust.domain.SanctionRepository;
import com.pitchmap.trust.domain.TrustErrorCode;
import com.pitchmap.trust.infra.MemberReportContextRow;
import com.pitchmap.trust.infra.MemberReportMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 접수에서 트랜잭션 하나로 묶는 부분이다. 신고 저장, 임시 정지 기록, 회원 상태 변경을 함께 커밋하거나 함께 되돌린다.
 * 세션 삭제는 {@link MemberReportService}가 이 트랜잭션이 끝난 뒤에 한다.
 */
@Service
@RequiredArgsConstructor
public class MemberReportApplier {

    private final MemberReportRepository memberReportRepository;
    private final SanctionRepository sanctionRepository;
    private final MemberReportMapper memberReportMapper;
    private final MemberSuspensionService memberSuspensionService;
    private final Clock clock;

    /**
     * 호출하면 reporterId인 회원의 신고를 접수 상태로 저장한다. 긴급 유형이면 같은 트랜잭션에서 대상 회원을 72시간 임시 정지하고
     * 그 기록을 남긴다.
     *
     * <p>다음 순서로 검사하고 처음 걸린 이유로 거부한다.
     * <ol>
     *   <li>신고 종류와 유형이 맞지 않거나, 후기 신고인데 후기 ID가 없거나, 회원 신고인데 후기 ID가 있으면 INVALID_INPUT
     *   <li>자기 자신을 신고하면 REPORT_NOT_ELIGIBLE
     *   <li>베이스캠프가 없거나, 신고자나 대상이 그 베이스캠프에 신청하거나 멤버였던 적이 없으면 REPORT_NOT_ELIGIBLE
     *   <li>후기 신고인데 후기가 그 베이스캠프에서 대상이 신고자에게 쓴 숨기지 않은 후기가 아니거나 아직 공개되지 않았으면 REPORT_NOT_ELIGIBLE
     *   <li>같은 신고를 이미 했으면 REPORT_DUPLICATED
     * </ol>
     */
    @Transactional
    public MemberReportResult apply(long reporterId, MemberReportCommand command) {
        requireConsistentKind(command);
        if (reporterId == command.targetMemberId()) {
            throw new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE);
        }
        MemberReportContextRow context = memberReportMapper
                .selectContext(
                        command.basecampId(),
                        reporterId,
                        command.targetMemberId(),
                        command.kind().name(),
                        command.companionReviewId())
                .orElseThrow(() -> new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE));
        Instant now = clock.instant();
        requireEligible(context, command, now);
        if (context.alreadyReported()) {
            throw new BusinessException(TrustErrorCode.REPORT_DUPLICATED);
        }
        Instant suspensionEndsAt = command.type().isUrgent() ? suspendTarget(command.targetMemberId(), now) : null;
        MemberReport report = save(MemberReport.receive(
                reporterId,
                command.targetMemberId(),
                command.basecampId(),
                command.companionReviewId(),
                command.type(),
                command.content(),
                now));
        if (suspensionEndsAt != null) {
            sanctionRepository.save(Sanction.temporary(report.getTargetMemberId(), report.getId(), now));
        }
        return new MemberReportResult(report.getId(), report.getTargetMemberId(), suspensionEndsAt);
    }

    private static void requireConsistentKind(MemberReportCommand command) {
        if (command.kind() != command.type().kind()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "신고 종류와 유형이 맞지 않습니다.");
        }
        boolean isReviewReport = command.kind() == ReportKind.REVIEW;
        if (isReviewReport && command.companionReviewId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "후기를 신고하려면 동행 후기 ID가 필요합니다.");
        }
        if (!isReviewReport && command.companionReviewId() != null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "회원 신고에는 동행 후기 ID를 넣을 수 없습니다.");
        }
    }

    private static void requireEligible(MemberReportContextRow context, MemberReportCommand command, Instant now) {
        if (!context.reporterInvolved() || !context.targetInvolved()) {
            throw new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE);
        }
        if (command.kind() == ReportKind.REVIEW && !isReportableReview(context, now)) {
            throw new BusinessException(TrustErrorCode.REPORT_NOT_ELIGIBLE);
        }
    }

    // 신고자가 받은 후기여도 블라인드 공개 전이면 신고자는 그 내용을 볼 수 없다. 그래서 공개된 후기만 신고할 수 있다.
    private static boolean isReportableReview(MemberReportContextRow context, Instant now) {
        return context.reviewMatches()
                && context.completedAt() != null
                && CompanionReviewPolicy.isRevealed(context.reverseWritten(), context.completedAt(), now);
    }

    private MemberReport save(MemberReport report) {
        try {
            return memberReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw MemberReportUniqueConstraintTranslator.translate(e);
        }
    }

    // 신고와 제재 행을 넣기 전에 대상 회원 행의 쓰기 잠금부터 잡는다. 행을 넣으면 외래 키 때문에 회원 행에 읽기 잠금이 먼저 걸리는데,
    // 서로 다른 신고자가 같은 대상을 동시에 신고하면 둘 다 읽기 잠금을 쥔 채 쓰기 잠금을 기다리다가 데드락이 난다.
    private Instant suspendTarget(long targetMemberId, Instant now) {
        Instant endsAt = now.plus(Sanction.TEMPORARY_SUSPENSION);
        memberSuspensionService.suspendTemporarily(targetMemberId, endsAt);
        return endsAt;
    }
}
