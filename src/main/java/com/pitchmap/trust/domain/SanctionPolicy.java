package com.pitchmap.trust.domain;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import java.util.List;

/**
 * 확정 제재의 단계를 정한다. 단계는 경고, 7일 정지, 30일 정지, 영구 정지 순서로 올라가고 4단계가 끝이다.
 *
 * <p>다음 단계는 이미 확정된 제재 가운데 가장 높은 단계에 1을 더한 값이다. 이 계산에 넣는 제재는 관리자가 해제하지 않은 확정 제재(적용 중이거나
 * 기간이 끝난 것)이고, 서버가 먼저 내리는 임시 정지는 넣지 않는다. 호출하는 쪽이 그 기준으로 가장 높은 단계를 읽어 넘긴다.
 */
public final class SanctionPolicy {

    /** 단계 순서대로 나열한 제재 종류다. 인덱스에 1을 더하면 단계다. */
    private static final List<SanctionType> TYPES_BY_LEVEL =
            List.of(SanctionType.WARNING, SanctionType.SUSPEND_7D, SanctionType.SUSPEND_30D, SanctionType.PERMANENT);

    /** 가장 높은 단계이자 영구 정지의 단계다. */
    public static final int MAX_LEVEL = TYPES_BY_LEVEL.size();

    private SanctionPolicy() {}

    /**
     * 호출하면 이번에 내릴 제재의 단계를 돌려준다. 확정된 제재가 없으면 1이고, 이미 가장 높은 단계까지 갔으면 4다.
     *
     * @param highestConfirmedLevel 이미 확정된 제재 중 가장 높은 단계. 확정된 제재가 없으면 null
     */
    public static int nextLevel(Integer highestConfirmedLevel) {
        if (highestConfirmedLevel == null) {
            return 1;
        }
        return Math.min(highestConfirmedLevel + 1, MAX_LEVEL);
    }

    /** 호출하면 단계(1~4)에 해당하는 제재 종류를 돌려준다. */
    public static SanctionType typeOf(int level) {
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("제재 단계는 1~" + MAX_LEVEL + "이어야 합니다. level=" + level);
        }
        return TYPES_BY_LEVEL.get(level - 1);
    }

    /** 호출하면 확정 제재 종류의 단계(1~4)를 돌려준다. 임시 정지는 단계가 없어 거부한다. */
    public static int levelOf(SanctionType type) {
        int index = TYPES_BY_LEVEL.indexOf(type);
        if (index < 0) {
            throw new IllegalArgumentException("단계가 없는 제재 종류입니다. type=" + type);
        }
        return index + 1;
    }

    /**
     * 호출하면 요청한 종류로 제재를 내려도 되는지 확인하고, 그 제재의 단계를 돌려준다.
     *
     * <p>요청한 종류가 계산된 다음 단계의 종류와 같으면 허용한다. 영구 정지는 심각한 위반일 때 단계를 건너뛰어 곧바로 지정할 수 있다.
     * 그 밖의 종류이거나 임시 정지이면 INVALID_INPUT으로 거부한다.
     *
     * @param highestConfirmedLevel 이미 확정된 제재 중 가장 높은 단계. 확정된 제재가 없으면 null
     */
    public static int decideLevel(Integer highestConfirmedLevel, SanctionType requested) {
        if (requested == null || requested == SanctionType.TEMPORARY_72H) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "관리자가 내릴 수 없는 제재 종류입니다.");
        }
        int next = nextLevel(highestConfirmedLevel);
        if (requested != SanctionType.PERMANENT && requested != typeOf(next)) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT, "이 회원의 다음 제재 단계는 " + typeOf(next) + "입니다. 요청한 종류와 다릅니다.");
        }
        return levelOf(requested);
    }
}
