package com.pitchmap.trust.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportTypeTest {

    @Test
    @DisplayName("[SN-01] 회원 신고 유형 6개는 MEMBER 종류이고, INAPPROPRIATE_REVIEW만 REVIEW 종류다")
    void kindOfEachType() {
        List<ReportType> memberTypes = Arrays.stream(ReportType.values())
                .filter(type -> type != ReportType.INAPPROPRIATE_REVIEW)
                .toList();

        assertThat(memberTypes).hasSize(6).allMatch(type -> type.kind() == ReportKind.MEMBER);
        assertThat(ReportType.INAPPROPRIATE_REVIEW.kind()).isEqualTo(ReportKind.REVIEW);
    }

    @Test
    @DisplayName("[SN-05] 성희롱·위협만 긴급 유형이다")
    void onlyHarassmentOrThreatIsUrgent() {
        assertThat(Arrays.stream(ReportType.values()).filter(ReportType::isUrgent))
                .containsExactly(ReportType.HARASSMENT_OR_THREAT);
    }
}
