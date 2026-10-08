package com.pitchmap.spot.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.spot.domain.BakjiConfirmation;
import com.pitchmap.spot.domain.BakjiConfirmationRepository;
import com.pitchmap.spot.domain.BakjiReport;
import com.pitchmap.spot.domain.BakjiReportReason;
import com.pitchmap.spot.domain.BakjiReportRepository;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.domain.SpotErrorCode;
import com.pitchmap.spot.domain.SpotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 박지를 확인하거나 신고한다. 제보자가 자기 박지를 확인하거나 신고하는 것도 막지 않는다.
 *
 * <p>확인하거나 신고할 수 있는 박지는 지도에 보이는(ACTIVE) 박지뿐이다. 장소가 없거나, 박지가 아니거나, ACTIVE가 아니면 서비스는
 * NOT_FOUND로 거부한다. 숨긴 박지가 있는지 드러내지 않으려고 이 경우들을 구분하지 않는다. 그래서 검토 대기로 바뀐 박지는 더 신고할 수 없다.
 *
 * <p>한 회원은 같은 박지를 한 번만 확인하고 한 번만 신고한다. 서비스는 먼저 이미 했는지 조회해서 거부한다. 하지만 같은 회원의 요청 두 개가
 * 동시에 조회를 통과할 수 있으므로, 저장할 때 DB가 던지는 기본 키·유니크 제약 위반도 같은 중복 오류로 바꾼다.
 *
 * <p>검토 전 신고가 {@value #PENDING_REVIEW_REPORT_THRESHOLD}건에 이르면 서비스는 같은 트랜잭션에서 박지를 검토 대기로 바꿔 지도, 목록, 상세에서
 * 뺀다. 신고 수를 따로 저장하지 않고 신고 행을 센다. 이때 관리자가 복구하면서 검토를 마친 것으로 표시한 신고는 세지 않는다. 그런데 InnoDB의 기본 격리 수준(REPEATABLE READ)에서는 트랜잭션이 첫 일반 SELECT를
 * 실행한 시점의 스냅샷을 읽는다. 그래서 4번째와 5번째 신고가 동시에 들어오면 두 트랜잭션이 서로의 미커밋 행을 보지 못해 둘 다 4건으로 세고,
 * 검토 대기로 바꾸는 일을 놓친다. 이를 막으려고 신고 트랜잭션은 첫 쿼리로 박지 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 같은
 * 박지의 신고가 줄을 서고, 뒤 트랜잭션은 앞 트랜잭션이 커밋한 뒤에 스냅샷을 만들어 커밋된 신고를 모두 센다. 그래서 이 잠금 쿼리를 어떤
 * 조회보다 앞에 둬야 한다. 확인은 지켜야 할 합계 규칙이 없어서 잠그지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BakjiFeedbackService {

    private static final int PENDING_REVIEW_REPORT_THRESHOLD = 5;

    private final SpotRepository spotRepository;
    private final BakjiConfirmationRepository bakjiConfirmationRepository;
    private final BakjiReportRepository bakjiReportRepository;
    private final Clock clock;

    /** 호출하면 memberId인 회원의 확인을 저장하고, 저장한 뒤 그 박지의 확인 수를 돌려준다. 이미 확인했으면 BAKJI_ALREADY_CONFIRMED로 거부한다. */
    @Transactional
    public long confirm(long memberId, long spotId) {
        Spot spot = spotRepository
                .findById(spotId)
                .filter(Spot::isActiveBakji)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (bakjiConfirmationRepository.existsBySpotIdAndMemberId(spot.getId(), memberId)) {
            throw alreadyConfirmed();
        }
        saveConfirmation(BakjiConfirmation.of(spot.getId(), memberId, clock.instant()));
        return bakjiConfirmationRepository.countBySpotId(spot.getId());
    }

    /**
     * 호출하면 memberId인 회원의 신고를 저장한다. 저장한 뒤 검토 전 신고가 {@value #PENDING_REVIEW_REPORT_THRESHOLD}건 이상이면 박지를 검토 대기로
     * 바꾼다. 이미 신고했으면 BAKJI_ALREADY_REPORTED로, 모르는 사유이면 INVALID_INPUT으로 거부한다.
     */
    @Transactional
    public void reportProblem(long memberId, long spotId, BakjiProblemReportCommand command) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
        Spot spot = spotRepository
                .findByIdForUpdate(spotId)
                .filter(Spot::isActiveBakji)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        BakjiReportReason reason = parseReason(command.reason());
        if (bakjiReportRepository.existsBySpotIdAndReporterId(spot.getId(), memberId)) {
            throw alreadyReported();
        }
        Instant now = clock.instant();
        saveReport(BakjiReport.of(spot.getId(), memberId, reason, command.content(), now));
        if (bakjiReportRepository.countBySpotIdAndReviewedAtIsNull(spot.getId()) >= PENDING_REVIEW_REPORT_THRESHOLD) {
            spot.markPendingReview(now);
        }
    }

    private void saveConfirmation(BakjiConfirmation confirmation) {
        try {
            bakjiConfirmationRepository.saveAndFlush(confirmation);
        } catch (DataIntegrityViolationException e) {
            throw alreadyConfirmed();
        }
    }

    private void saveReport(BakjiReport report) {
        try {
            bakjiReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw alreadyReported();
        }
    }

    private static BakjiReportReason parseReason(String value) {
        return Arrays.stream(BakjiReportReason.values())
                .filter(reason -> reason.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.INVALID_INPUT, "신고 사유는 " + allowedReasons() + " 중 하나여야 합니다."));
    }

    private static String allowedReasons() {
        return String.join(
                ", ", Arrays.stream(BakjiReportReason.values()).map(Enum::name).toList());
    }

    private static BusinessException alreadyConfirmed() {
        return new BusinessException(SpotErrorCode.BAKJI_ALREADY_CONFIRMED);
    }

    private static BusinessException alreadyReported() {
        return new BusinessException(SpotErrorCode.BAKJI_ALREADY_REPORTED);
    }
}
