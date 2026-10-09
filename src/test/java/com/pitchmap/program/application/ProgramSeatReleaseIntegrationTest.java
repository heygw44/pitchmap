package com.pitchmap.program.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramSeatReleaseIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Autowired
    private ProgramPaymentService programPaymentService;

    @Autowired
    private ProgramPaymentExpiryService expiryService;

    @Autowired
    private ProgramAdminService programAdminService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    private ProgramApplyFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ProgramApplyFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-20][PG-07] 본인 취소로 자리가 돌아오면 이벤트가 1건 생기고, 이미 신청이 있는 알림 신청자는 빠지며 알림 시각이 적힌다")
    void userCancelRecordsSeatReleased() {
        // given
        long programId = fixture.saveProgram(2, false);
        long canceler = fixture.saveMember();
        long holder = fixture.saveMember();
        long waiting = fixture.saveMember();
        long cancelApplicationId = fixture.saveApplication(programId, canceler, "PENDING_PAYMENT");
        fixture.saveApplication(programId, holder, "CONFIRMED");
        fixture.saveVacancyAlert(programId, waiting);
        fixture.saveVacancyAlert(programId, holder);
        // 취소한 회원도 알림 신청자다. 같은 트랜잭션에서 바꾼 신청 상태가 대상 조회 전에 반영되는지 확인한다.
        fixture.saveVacancyAlert(programId, canceler);

        // when
        programPaymentService.cancel(canceler, cancelApplicationId);

        // then
        assertSeatReleased(programId, List.of(canceler, waiting));
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isEqualTo(LocalDateTime.of(2026, 10, 5, 3, 0));
        assertThat(fixture.vacancyAlertNotifiedAt(programId, holder)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 결제 기한 만료로 자리가 돌아와도 같은 이벤트가 1건 생긴다")
    void expiryRecordsSeatReleased() {
        // given
        long programId = fixture.saveProgram(2, false);
        long expiring = fixture.saveMember();
        long holder = fixture.saveMember();
        long waiting = fixture.saveMember();
        fixture.saveApplication(programId, expiring, "PENDING_PAYMENT", NOW.minus(Duration.ofMinutes(1)));
        fixture.saveApplication(programId, holder, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, waiting);
        fixture.saveVacancyAlert(programId, holder);
        fixture.saveVacancyAlert(programId, expiring);

        // when
        expiryService.run();

        // then
        assertSeatReleased(programId, List.of(expiring, waiting));
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isNotNull();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, holder)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 관리자가 행사를 취소하면 자리가 생긴 것이 아니므로 이벤트가 없다")
    void adminProgramCancelRecordsNothing() {
        long programId = fixture.saveProgram(2, false);
        long applicant = fixture.saveMember();
        long waiting = fixture.saveMember();
        fixture.saveApplication(programId, applicant, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, waiting);

        programAdminService.cancel(fixture.saveMember(), programId);

        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 알림 신청자가 없으면 이벤트가 없다")
    void noSubscribersRecordsNothing() {
        long programId = fixture.saveProgram(2, false);
        long canceler = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, canceler, "PENDING_PAYMENT");

        programPaymentService.cancel(canceler, applicationId);

        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
    }

    @Test
    @DisplayName("[F-20][PG-07] 알림 신청자가 모두 결제 대기·확정 신청을 가졌으면 이벤트가 없다")
    void allSubscribersHoldSeatsRecordsNothing() {
        long programId = fixture.saveProgram(3, false);
        long canceler = fixture.saveMember();
        long holder = fixture.saveMember();
        long pending = fixture.saveMember();
        long applicationId = fixture.saveApplication(programId, canceler, "PENDING_PAYMENT");
        fixture.saveApplication(programId, holder, "CONFIRMED");
        fixture.saveApplication(programId, pending, "PENDING_PAYMENT");
        fixture.saveVacancyAlert(programId, holder);
        fixture.saveVacancyAlert(programId, pending);

        programPaymentService.cancel(canceler, applicationId);

        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, holder)).isNull();
    }

    @Test
    @DisplayName("[F-20][PG-07] 신청 마감 시각이 지난 뒤의 본인 취소와 결제 만료는 이벤트를 만들지 않는다")
    void afterApplyCloseRecordsNothing() {
        // given
        long programId = fixture.saveProgram(
                2, false, NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofHours(1)), "SCHEDULED");
        long canceler = fixture.saveMember();
        long expiring = fixture.saveMember();
        long waiting = fixture.saveMember();
        long cancelApplicationId = fixture.saveApplication(programId, canceler, "PENDING_PAYMENT");
        fixture.saveApplication(programId, expiring, "PENDING_PAYMENT", NOW.plus(Duration.ofMinutes(90)));
        fixture.saveVacancyAlert(programId, waiting);
        clock.advance(Duration.ofMinutes(61));

        // when
        programPaymentService.cancel(canceler, cancelApplicationId);
        clock.advance(Duration.ofMinutes(30));
        expiryService.run();

        // then
        assertThat(fixture.applicationStatus(cancelApplicationId)).isEqualTo("CANCELED");
        assertThat(fixture.seatReleasedPayloads(programId)).isEmpty();
        assertThat(fixture.vacancyAlertNotifiedAt(programId, waiting)).isNull();
    }

    private void assertSeatReleased(long programId, List<Long> expectedMemberIds) {
        List<String> payloads = fixture.seatReleasedPayloads(programId);
        assertThat(payloads).hasSize(1);
        assertThat(JsonPath.<Number>read(payloads.get(0), "$.programId").longValue())
                .isEqualTo(programId);
        List<Number> memberIds = JsonPath.read(payloads.get(0), "$.memberIds");
        assertThat(memberIds.stream().map(Number::longValue).toList()).containsExactlyElementsOf(expectedMemberIds);
    }
}
