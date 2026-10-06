package com.pitchmap.spot.application;

import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.ParkAreaJudgement;
import com.pitchmap.spot.infra.BakjiPointRow;
import com.pitchmap.spot.infra.ParkAreaJudgeMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 박지 좌표가 공원 경계 안에 있는지 판정한다.
 *
 * <p>서버는 박지를 저장할 때 한 번 판정하고, 경고 여부, 근거 경계, 판정 시각을 박지 행에 저장한다. 지도와 상세를 조회할 때는 저장한 결과만 읽고 다시
 * 계산하지 않는다. 경계를 새 데이터로 다시 적재하면 재판정 작업이 {@link #rejudgeBakjis()}를 호출해서 모든 박지를 새 경계로 다시 판정한다.
 *
 * <p>경계가 겹치는 곳의 좌표는 여러 경계에 들어간다. 이때 서버는 ID가 가장 작은 경계를 근거로 고른다.
 */
@Service
@RequiredArgsConstructor
public class ParkAreaJudgeService {

    private static final int UPDATE_CHUNK_SIZE = 1_000;

    private final ParkAreaJudgeMapper parkAreaJudgeMapper;
    private final Clock clock;

    /** 호출하면 location을 포함하는 공원 경계를 찾아 판정 결과를 돌려준다. 판정 시각은 호출한 시각이다. */
    @Transactional(readOnly = true)
    public ParkAreaJudgement judge(GeoPoint location) {
        Instant now = clock.instant();
        Long protectedAreaId = parkAreaJudgeMapper.selectContainingAreaId(location.latitude(), location.longitude());
        if (protectedAreaId == null) {
            return ParkAreaJudgement.outside(now);
        }
        return ParkAreaJudgement.inside(protectedAreaId, now);
    }

    /**
     * 호출하면 상태와 상관없이 모든 박지를 지금 경계로 다시 판정하고, 판정한 박지 수를 돌려준다.
     *
     * <p>서비스는 박지 좌표를 한 번 읽고, 박지마다 공간 인덱스를 쓰는 점 조회로 경계를 찾는다. 겹친 경계에서는 ID가 가장 작은 경계를 근거로 고른다. 그다음
     * 같은 경계로 판정된 박지끼리 묶어서 {@value #UPDATE_CHUNK_SIZE}건씩 나눠 기록한다. 모든 박지의 판정 시각은 호출을 시작한 시각 하나로 같다.
     *
     * <p>예전에 경고를 받았더라도 지금 경계에 들어가지 않으면 서비스는 경고를 끄고 근거 경계를 비운다. 판정은 제보 내용을 고치는 일이 아니라서 updated_at은
     * 바꾸지 않는다.
     */
    @Transactional
    public int rejudgeBakjis() {
        Instant now = clock.instant();
        List<BakjiPointRow> points = parkAreaJudgeMapper.selectBakjiPoints();

        // 경계 밖 박지는 null 키로 묶는다.
        Map<Long, List<Long>> spotIdsByAreaId = new HashMap<>();
        for (BakjiPointRow point : points) {
            Long protectedAreaId = parkAreaJudgeMapper.selectContainingAreaId(point.lat(), point.lng());
            spotIdsByAreaId
                    .computeIfAbsent(protectedAreaId, key -> new ArrayList<>())
                    .add(point.id());
        }

        for (Map.Entry<Long, List<Long>> entry : spotIdsByAreaId.entrySet()) {
            List<Long> spotIds = entry.getValue();
            for (int from = 0; from < spotIds.size(); from += UPDATE_CHUNK_SIZE) {
                int to = Math.min(from + UPDATE_CHUNK_SIZE, spotIds.size());
                parkAreaJudgeMapper.updateJudgement(spotIds.subList(from, to), entry.getKey(), now);
            }
        }
        return points.size();
    }
}
