package com.pitchmap.spot.application;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.domain.SpotRepository;
import com.pitchmap.spot.domain.SpotStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 모듈이 장소에 무언가를 붙이기 전에, 그 장소가 지도에 보이는지 확인하는 공개 서비스.
 *
 * <p>다른 모듈은 spot 모듈의 도메인을 참조할 수 없으므로 장소 상태를 직접 읽지 못한다. 그래서 이 서비스가 대신 확인한다.
 */
@Service
@RequiredArgsConstructor
public class ActiveSpotChecker {

    private final SpotRepository spotRepository;

    /**
     * 호출하면 spotId인 장소가 ACTIVE인지 확인한다. 장소가 없거나 ACTIVE가 아니면 NOT_FOUND로 거부한다.
     *
     * <p>숨김, 삭제, 검토 대기 장소가 있다는 사실을 드러내지 않으려고, 없는 장소와 ACTIVE가 아닌 장소를 구분하지 않는다.
     */
    @Transactional(readOnly = true)
    public void requireActive(long spotId) {
        spotRepository
                .findById(spotId)
                .filter(ActiveSpotChecker::isActive)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private static boolean isActive(Spot spot) {
        return spot.getStatus() == SpotStatus.ACTIVE;
    }
}
