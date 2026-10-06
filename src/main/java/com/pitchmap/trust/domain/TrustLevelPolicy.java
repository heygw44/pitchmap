package com.pitchmap.trust.domain;

/** 신뢰 단계를 판정한다. 단계는 저장하지 않고 호출할 때마다 계산한다. */
public final class TrustLevelPolicy {

    private TrustLevelPolicy() {}

    /**
     * 본인확인을 마친 성인이 아니면 0, 성인이면 1, 성인이면서 동행 기록이 단계 2 조건을 채우면 2다.
     */
    public static int judge(boolean identityVerified, boolean adult, CompanionRecord record) {
        if (!identityVerified || !adult) {
            return 0;
        }
        if (record.meetsTrustedCondition()) {
            return 2;
        }
        return 1;
    }
}
