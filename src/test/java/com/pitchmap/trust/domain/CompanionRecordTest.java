package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompanionRecordTest {

    @Test
    @DisplayName("[TR-01] 받은 후기가 없으면 다시 동행 비율은 null이고 단계 2 조건을 채우지 못한다")
    void noReviewsHasNoRateAndIsNotTrusted() {
        CompanionRecord record = new CompanionRecord(5, 0, 0, false);

        assertThat(record.rejoinRate()).isNull();
        assertThat(record.meetsTrustedCondition()).isFalse();
    }

    @Test
    @DisplayName("[TR-01] 비율은 정수 %로 버린다. 3건 중 2건이면 66이다")
    void rateIsFloored() {
        assertThat(new CompanionRecord(3, 3, 2, false).rejoinRate()).isEqualTo(66);
        assertThat(new CompanionRecord(3, 5, 4, false).rejoinRate()).isEqualTo(80);
    }

    @Test
    @DisplayName("[TR-01] 완료한 동행이 2회면 부족하고 3회면 충분하다")
    void completedCompanionsThreshold() {
        assertThat(new CompanionRecord(2, 5, 5, false).meetsTrustedCondition()).isFalse();
        assertThat(new CompanionRecord(3, 5, 5, false).meetsTrustedCondition()).isTrue();
    }

    @Test
    @DisplayName("[TR-01] 다시 동행 비율은 80%까지 채워야 한다. 5건 중 4건은 충족, 100건 중 79건은 미충족이다")
    void rejoinRateThreshold() {
        assertThat(new CompanionRecord(3, 5, 4, false).meetsTrustedCondition()).isTrue();
        assertThat(new CompanionRecord(3, 100, 79, false).meetsTrustedCondition())
                .isFalse();
    }

    @Test
    @DisplayName("[TR-01] 반올림하면 80이 되는 79.5%도 개수로 비교해서 미충족이다")
    void comparesCountsNotRoundedRate() {
        // 200건 중 159건은 79.5%다.
        CompanionRecord record = new CompanionRecord(3, 200, 159, false);

        assertThat(record.rejoinRate()).isEqualTo(79);
        assertThat(record.meetsTrustedCondition()).isFalse();
    }

    @Test
    @DisplayName("[TR-01] 최근 확정 제재가 있으면 다른 조건을 모두 채워도 미충족이다")
    void recentSanctionBlocks() {
        assertThat(new CompanionRecord(3, 5, 5, true).meetsTrustedCondition()).isFalse();
    }

    @Test
    @DisplayName("[TR-01] 동행·후기·제재 기록이 없는 상태는 비율 null, 단계 2 미충족이다")
    void noneConstant() {
        assertThat(CompanionRecord.NONE.rejoinRate()).isNull();
        assertThat(CompanionRecord.NONE.meetsTrustedCondition()).isFalse();
    }
}
