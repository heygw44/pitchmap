package com.pitchmap.spot.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 장소 좌표가 공원 경계 안에 있는지 판정한 결과.
 *
 * <p>좌표를 포함하는 경계가 있으면 protectedAreaId에 그 경계의 ID가 들어가고, 없으면 null이다. 경고 여부는 경계 ID가 있는지로만 정해서, 경고가
 * 켜졌는데 근거 경계가 없거나 경계가 있는데 경고가 꺼진 결과는 만들 수 없다.
 *
 * @param protectedAreaId 좌표를 포함하는 공원 경계의 ID. 포함하는 경계가 없으면 null
 * @param checkedAt 판정한 시각(UTC)
 */
public record ParkAreaJudgement(Long protectedAreaId, Instant checkedAt) {

    public ParkAreaJudgement {
        Objects.requireNonNull(checkedAt, "판정 시각이 없습니다.");
    }

    /** 호출하면 좌표가 protectedAreaId 경계 안에 있다는 판정을 돌려준다. */
    public static ParkAreaJudgement inside(long protectedAreaId, Instant checkedAt) {
        return new ParkAreaJudgement(protectedAreaId, checkedAt);
    }

    /** 호출하면 좌표를 포함하는 경계가 없다는 판정을 돌려준다. */
    public static ParkAreaJudgement outside(Instant checkedAt) {
        return new ParkAreaJudgement(null, checkedAt);
    }

    /** 좌표가 공원 경계 안일 가능성이 있어 경고를 붙여야 하면 true다. */
    public boolean parkWarning() {
        return protectedAreaId != null;
    }
}
