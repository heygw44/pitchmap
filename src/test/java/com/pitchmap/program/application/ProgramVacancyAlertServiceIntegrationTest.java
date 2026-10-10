package com.pitchmap.program.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.program.domain.ProgramErrorCode;
import com.pitchmap.program.domain.ProgramException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramVacancyAlertServiceIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Autowired
    private ProgramVacancyAlertService vacancyAlertService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-20][PG-07] 처음 신청하면 true이고, 다시 신청하면 false이며 행은 하나뿐이다")
    void subscribeIsIdempotent() {
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();

        boolean first = vacancyAlertService.subscribe(memberId, programId);
        boolean second = vacancyAlertService.subscribe(memberId, programId);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(fixture.vacancyAlertCount(programId, memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-20][PG-07] 남은 자리가 있어도 신청을 저장한다")
    void subscribeStoresEvenWhenSeatsRemain() {
        long programId = fixture.saveProgram(5, false);
        long memberId = fixture.saveMember();

        assertThat(vacancyAlertService.subscribe(memberId, programId)).isTrue();
        assertThat(fixture.activeCount(programId)).isZero();
    }

    @Test
    @DisplayName("[F-20][PG-07] 취소된 행사에는 PROGRAM_INVALID_STATE이다")
    void subscribeRejectsCanceledProgram() {
        long programId =
                fixture.saveProgram(3, false, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofDays(5)), "CANCELED");
        long memberId = fixture.saveMember();

        assertThatThrownBy(() -> vacancyAlertService.subscribe(memberId, programId))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException) e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PROGRAM_INVALID_STATE);
        assertThat(fixture.vacancyAlertCount(programId, memberId)).isZero();
    }

    @Test
    @DisplayName("[F-20][PG-07] 신청 마감 시각 정각부터는 PROGRAM_INVALID_STATE이다")
    void subscribeRejectsAfterApplyClose() {
        long programId = fixture.saveProgram(3, false, NOW.minus(Duration.ofDays(1)), NOW, "SCHEDULED");
        long memberId = fixture.saveMember();

        assertThatThrownBy(() -> vacancyAlertService.subscribe(memberId, programId))
                .isInstanceOf(ProgramException.class)
                .extracting(e -> ((ProgramException) e).getErrorCode())
                .isEqualTo(ProgramErrorCode.PROGRAM_INVALID_STATE);
    }

    @Test
    @DisplayName("[F-20][PG-07] 없는 행사이면 NOT_FOUND이다")
    void missingProgramIsNotFound() {
        long memberId = fixture.saveMember();

        assertThatThrownBy(() -> vacancyAlertService.subscribe(memberId, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> vacancyAlertService.unsubscribe(memberId, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("[F-20][PG-07] 해제하면 행이 지워지고, 신청한 적이 없어도 오류가 없으며, 해제 뒤 다시 신청할 수 있다")
    void unsubscribeRemovesAndAllowsResubscribe() {
        long programId = fixture.saveProgram(3, false);
        long memberId = fixture.saveMember();
        vacancyAlertService.subscribe(memberId, programId);

        vacancyAlertService.unsubscribe(memberId, programId);
        vacancyAlertService.unsubscribe(memberId, programId);

        assertThat(fixture.vacancyAlertCount(programId, memberId)).isZero();
        assertThat(vacancyAlertService.subscribe(memberId, programId)).isTrue();
        assertThat(fixture.vacancyAlertCount(programId, memberId)).isEqualTo(1);
    }
}
