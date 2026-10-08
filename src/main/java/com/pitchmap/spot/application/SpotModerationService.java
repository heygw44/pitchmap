package com.pitchmap.spot.application;

import com.pitchmap.common.audit.AdminAuditAction;
import com.pitchmap.common.audit.AdminAuditRecorder;
import com.pitchmap.common.audit.AdminAuditTargetType;
import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.spot.domain.BakjiReportRepository;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.domain.SpotRepository;
import com.pitchmap.spot.domain.SpotStatus;
import com.pitchmap.spot.infra.PublicSpotMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자가 장소를 숨기거나 복구한다.
 *
 * <p>두 메서드 모두 첫 쿼리로 장소 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 읽는다. 박지 신고도 같은 행을 먼저 잠그므로, 복구와
 * 신고가 줄을 선다. 그래서 복구 전에 커밋된 신고는 검토를 마친 것으로 표시되고, 복구 뒤의 신고는 새로 센다. 같은 장소를 동시에 숨기면 뒤
 * 요청은 잠금을 기다린 뒤 이미 숨긴 상태를 보고 거부된다. 장소가 없거나 제보자가 지운 장소이면 존재를 드러내지 않으려고 NOT_FOUND로
 * 거부하고, 상태 전이를 할 수 없으면 SPOT_INVALID_STATE로 거부한다.
 *
 * <p>공공데이터 장소는 원천 삭제 표시를 지운다. 동기화가 원천에서 사라진 장소를 숨기면서 이 표시를 남기는데, 표시가 남아 있으면 관리자가
 * 바꾼 상태를 동기화가 다시 되돌릴 수 있기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotModerationService {

    private final SpotRepository spotRepository;
    private final BakjiReportRepository bakjiReportRepository;
    private final PublicSpotMapper publicSpotMapper;
    private final AdminAuditRecorder adminAuditRecorder;
    private final Clock clock;

    /** 호출하면 ACTIVE나 검토 대기 장소를 HIDDEN으로 바꾸고 감사 로그를 남긴다. 장소 종류는 묻지 않는다. */
    @Transactional
    public SpotStatusResult hide(long spotId, long adminId) {
        // 잠금 쿼리가 이 트랜잭션의 첫 쿼리여야 한다. 클래스 설명을 본다.
        Spot spot = lockSpot(spotId);
        SpotStatus before = spot.getStatus();
        Instant now = clock.instant();
        spot.hide(now);
        clearSourceRemovedIfPublicData(spot, now);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.SPOT_HIDE,
                AdminAuditTargetType.SPOT,
                spotId,
                new SpotAuditDetails.Hide(
                        spot.getType().name(), before.name(), spot.getStatus().name()));
        log.info("spot hidden spotId={} adminId={}", spotId, adminId);
        return new SpotStatusResult(spotId, spot.getStatus().name());
    }

    /** 호출하면 검토 대기나 HIDDEN 장소를 ACTIVE로 바꾸고, 그 장소의 검토 전 신고를 모두 검토를 마친 것으로 표시한 뒤 감사 로그를 남긴다. */
    @Transactional
    public SpotStatusResult restore(long spotId, long adminId) {
        Spot spot = lockSpot(spotId);
        SpotStatus before = spot.getStatus();
        Instant now = clock.instant();
        spot.restore(now);
        clearSourceRemovedIfPublicData(spot, now);
        // 이 쿼리가 실행되기 전에 JPA가 위의 상태 변경을 플러시한다. 신고 엔티티는 읽지 않아서 영속성 컨텍스트를 비울 필요가 없다.
        int reviewedReportCount = bakjiReportRepository.markReviewed(spotId, now);
        adminAuditRecorder.record(
                adminId,
                AdminAuditAction.SPOT_RESTORE,
                AdminAuditTargetType.SPOT,
                spotId,
                new SpotAuditDetails.Restore(
                        spot.getType().name(), before.name(), spot.getStatus().name(), reviewedReportCount));
        log.info("spot restored spotId={} adminId={} reviewedReportCount={}", spotId, adminId, reviewedReportCount);
        return new SpotStatusResult(spotId, spot.getStatus().name());
    }

    private Spot lockSpot(long spotId) {
        return spotRepository
                .findByIdForUpdate(spotId)
                .filter(spot -> !spot.isDeleted())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    // MyBatis로 갱신하는 public_spot_detail 행은 이 트랜잭션에서 엔티티로 읽지 않는다. 장소 엔티티의 상태 변경은 커밋 때 플러시된다.
    private void clearSourceRemovedIfPublicData(Spot spot, Instant now) {
        if (spot.isPublicData()) {
            publicSpotMapper.clearSourceRemoved(List.of(spot.getId()), now);
        }
    }
}
