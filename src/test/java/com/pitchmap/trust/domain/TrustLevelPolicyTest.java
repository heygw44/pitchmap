package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrustLevelPolicyTest {

    private static final CompanionRecord ALL_MET = new CompanionRecord(3, 5, 5, 0, false);

    @Test
    @DisplayName("[TR-01] 본인확인 전이면 동행 기록이 좋아도 단계 0이다")
    void notVerifiedIsLevelZero() {
        assertThat(TrustLevelPolicy.judge(false, false, ALL_MET)).isZero();
    }

    @Test
    @DisplayName("[TR-01][ID-03] 본인확인을 마친 미성년은 단계 0이다")
    void minorIsLevelZero() {
        assertThat(TrustLevelPolicy.judge(true, false, ALL_MET)).isZero();
    }

    @Test
    @DisplayName("[TR-01] 본인확인을 마친 성인은 동행 기록이 없으면 단계 1이다")
    void adultWithoutRecordIsLevelOne() {
        assertThat(TrustLevelPolicy.judge(true, true, CompanionRecord.NONE)).isEqualTo(1);
    }

    @Test
    @DisplayName("[TR-01] 완료 동행 3회, 다시 동행 80%, 최근 제재 없음을 모두 채운 성인은 단계 2다")
    void allConditionsMetIsLevelTwo() {
        assertThat(TrustLevelPolicy.judge(true, true, ALL_MET)).isEqualTo(2);
    }

    @Test
    @DisplayName("[TR-01] 최근 확정 제재가 있으면 단계 1이다")
    void recentSanctionIsLevelOne() {
        assertThat(TrustLevelPolicy.judge(true, true, new CompanionRecord(3, 5, 5, 0, true)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("[BC-21][TR-01] 최근 임박 탈퇴가 3회면 다른 조건을 채워도 단계 1이다")
    void threeRecentEarlyLeavesIsLevelOne() {
        assertThat(TrustLevelPolicy.judge(true, true, new CompanionRecord(3, 5, 5, 3, false)))
                .isEqualTo(1);
    }
}
