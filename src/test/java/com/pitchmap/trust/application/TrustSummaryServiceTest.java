package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.trust.domain.CiHash;
import com.pitchmap.trust.domain.Gender;
import com.pitchmap.trust.domain.IdentityProviderType;
import com.pitchmap.trust.domain.IdentityVerification;
import com.pitchmap.trust.domain.IdentityVerificationRepository;
import com.pitchmap.trust.domain.VerifiedIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrustSummaryServiceTest {

    private static final long MEMBER_ID = 7L;

    private final IdentityVerificationRepository repository = mock(IdentityVerificationRepository.class);

    @Test
    @DisplayName("본인확인 기록이 없으면 본인확인 전, 신뢰 단계 0이다")
    void withoutRecordIsLevelZero() {
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        TrustSummary summary = serviceAt("2026-10-05T03:00:00Z").summarize(MEMBER_ID);

        assertThat(summary).isEqualTo(new TrustSummary(false, 0));
    }

    @Test
    @DisplayName("[ID-03] 본인확인을 마친 성인은 신뢰 단계 1이고, 미성년은 본인확인은 마쳤어도 단계 0이다")
    void adultIsLevelOneAndMinorIsLevelZero() {
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(recordBornIn(2007)));
        TrustSummaryService service = serviceAt("2026-10-05T03:00:00Z");
        assertThat(service.summarize(MEMBER_ID)).isEqualTo(new TrustSummary(true, 1));

        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(recordBornIn(2008)));
        assertThat(service.summarize(MEMBER_ID)).isEqualTo(new TrustSummary(true, 0));
    }

    @Test
    @DisplayName("[ID-03] 한국 시각으로 해가 바뀌는 순간(UTC 12월 31일 15시)에 2008년생이 성인이 된다")
    void yearBoundaryFollowsKoreaTime() {
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(recordBornIn(2007)));
        // 2007년생은 2026년에 성인이므로 경계는 2025년에서 2026년으로 넘어갈 때다.
        assertThat(serviceAt("2025-12-31T14:59:59Z").summarize(MEMBER_ID)).isEqualTo(new TrustSummary(true, 0));
        assertThat(serviceAt("2025-12-31T15:00:00Z").summarize(MEMBER_ID)).isEqualTo(new TrustSummary(true, 1));
    }

    @Test
    @DisplayName("[TR-01] 상세는 성인이면 본인확인 연령대·성별을, 미성년이면 성별만 내고 다시 동행 비율은 null이다")
    void detailExposesVerifiedValues() {
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(recordBornIn(2007)));
        TrustSummaryService service = serviceAt("2026-10-05T03:00:00Z");
        TrustDetail adult = service.detail(MEMBER_ID);
        assertThat(adult).isEqualTo(new TrustDetail(true, true, 1, 0, null, true, List.of(), "TWENTIES", "MALE"));

        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(recordBornIn(2008)));
        TrustDetail minor = service.detail(MEMBER_ID);
        assertThat(minor).isEqualTo(new TrustDetail(true, false, 0, 0, null, true, List.of(), null, "MALE"));
    }

    @Test
    @DisplayName("[TR-01] 본인확인 기록이 없으면 상세는 단계 0이고 본인확인 값이 없다")
    void detailWithoutRecord() {
        when(repository.findByMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        TrustDetail detail = serviceAt("2026-10-05T03:00:00Z").detail(MEMBER_ID);

        assertThat(detail).isEqualTo(new TrustDetail(false, false, 0, 0, null, true, List.of(), null, null));
    }

    private TrustSummaryService serviceAt(String instant) {
        return new TrustSummaryService(repository, MutableClock.at(Instant.parse(instant)));
    }

    private static IdentityVerification recordBornIn(int birthYear) {
        return IdentityVerification.verify(
                MEMBER_ID,
                new VerifiedIdentity("ci", birthYear, Gender.MALE),
                new CiHash("b".repeat(64)),
                IdentityProviderType.FAKE,
                MutableClock.DEFAULT_INSTANT);
    }
}
