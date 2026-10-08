package com.pitchmap.basecamp.application;

import static com.pitchmap.basecamp.application.BasecampAutoTransitionFixture.END_DATE;
import static com.pitchmap.basecamp.application.BasecampAutoTransitionFixture.START_DATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class BasecampAutoTransitionIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant DAY_BEFORE_DEPARTURE =
            START_DATE.minusDays(1).atStartOfDay(KST).toInstant();
    private static final Instant DAY_AFTER_END =
            END_DATE.plusDays(1).atStartOfDay(KST).toInstant();

    @Autowired
    private BasecampAutoTransitionService autoTransitionService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JsonMapper jsonMapper;

    private BasecampAutoTransitionFixture fixture;

    @BeforeEach
    void createFixture() {
        fixture = new BasecampAutoTransitionFixture(jdbc, memberRepository, jsonMapper);
    }

    @Test
    @DisplayName("[F-14][BC-10] 출발 전날 0시(KST) 1초 전에는 바꾸지 않고, 0시가 되면 처리한다")
    void processesOnlyFromMidnightOfDayBeforeDeparture() {
        long basecampId = fixture.saveBasecampWithMembers("RECRUITING", 1);

        clock.setInstant(DAY_BEFORE_DEPARTURE.minusSeconds(1));
        autoTransitionService.run();
        assertThat(fixture.statusOf(basecampId)).isEqualTo("RECRUITING");
        assertThat(fixture.totalEventCount()).isZero();

        clock.setInstant(DAY_BEFORE_DEPARTURE);
        autoTransitionService.run();
        assertThat(fixture.statusOf(basecampId)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("[F-14][BC-10] 인원이 2명이면 확정하고 확정 시각을 남기며, 대기 신청을 만료시키고 캠프 리더와 멤버에게 알릴 이벤트를 기록한다")
    void confirmsWithTwoActiveMembers() {
        long basecampId = fixture.saveBasecampWithMembers("RECRUITING", 1);
        long applicationId = fixture.insertPendingApplication(basecampId);
        clock.setInstant(DAY_BEFORE_DEPARTURE);

        BasecampAutoTransitionService.Result result = autoTransitionService.run();

        assertThat(result.departureProcessed()).isEqualTo(1);
        assertThat(jdbc.queryForMap(
                        "SELECT status, closed_reason, confirmed_at FROM basecamp WHERE id = ?", basecampId))
                .containsEntry("status", "CONFIRMED")
                .containsEntry("closed_reason", null)
                .doesNotContainEntry("confirmed_at", null);
        assertThat(fixture.applicationStatusOf(applicationId)).isEqualTo("EXPIRED");
        assertThat(fixture.eventCount(BasecampTransitionEvents.CONFIRMED_EVENT_TYPE, basecampId))
                .isEqualTo(1);
        assertThat(fixture.payloadMemberIdsOf(BasecampTransitionEvents.CONFIRMED_EVENT_TYPE, basecampId))
                .containsExactly(fixture.leaderIdOf(basecampId), fixture.firstMemberIdOf(basecampId));
    }

    @Test
    @DisplayName("[F-14][BC-10] 캠프 리더 혼자이면 인원 부족으로 취소하고, 대기 신청을 만료시키며 캠프 리더에게 알릴 이벤트를 기록한다")
    void cancelsWhenOnlyLeaderRemains() {
        long basecampId = fixture.saveBasecamp("RECRUITING");
        long applicationId = fixture.insertPendingApplication(basecampId);
        clock.setInstant(DAY_BEFORE_DEPARTURE);

        autoTransitionService.run();

        assertThat(jdbc.queryForMap("SELECT status, cancel_reason, canceled_at FROM basecamp WHERE id = ?", basecampId))
                .containsEntry("status", "CANCELED")
                .containsEntry("cancel_reason", "NOT_ENOUGH_MEMBERS")
                .doesNotContainEntry("canceled_at", null);
        assertThat(fixture.applicationStatusOf(applicationId)).isEqualTo("EXPIRED");
        assertThat(fixture.payloadMemberIdsOf(BasecampTransitionEvents.CANCELED_EVENT_TYPE, basecampId))
                .containsExactly(fixture.leaderIdOf(basecampId));
    }

    @Test
    @DisplayName("[F-14][BC-10] 탈퇴한 멤버를 뺀 ACTIVE 인원이 1명이면 취소하고 이벤트에 탈퇴한 멤버를 싣지 않는다")
    void cancelsWhenMemberHasLeft() {
        long basecampId = fixture.saveBasecamp("RECRUITING");
        fixture.insertMember(basecampId, fixture.saveVerifiedMember(), "MEMBER", "LEFT");
        clock.setInstant(DAY_BEFORE_DEPARTURE);

        autoTransitionService.run();

        assertThat(fixture.statusOf(basecampId)).isEqualTo("CANCELED");
        assertThat(fixture.payloadMemberIdsOf(BasecampTransitionEvents.CANCELED_EVENT_TYPE, basecampId))
                .containsExactly(fixture.leaderIdOf(basecampId));
    }

    @Test
    @DisplayName("[F-14][BC-10] 자동 마감과 캠프 리더 마감 상태에서도 확정하고 마감 사유를 비운다")
    void confirmsClosedBasecampsAndClearsClosedReason() {
        long autoFull = fixture.saveBasecampWithMembers("CLOSED", 2);
        jdbc.update("UPDATE basecamp SET closed_reason = 'AUTO_FULL' WHERE id = ?", autoFull);
        long byLeader = fixture.saveBasecampWithMembers("CLOSED", 1);
        jdbc.update("UPDATE basecamp SET closed_reason = 'LEADER' WHERE id = ?", byLeader);
        clock.setInstant(DAY_BEFORE_DEPARTURE);

        autoTransitionService.run();

        for (long basecampId : new long[] {autoFull, byLeader}) {
            assertThat(jdbc.queryForMap("SELECT status, closed_reason FROM basecamp WHERE id = ?", basecampId))
                    .containsEntry("status", "CONFIRMED")
                    .containsEntry("closed_reason", null);
        }
    }

    @Test
    @DisplayName("[F-14][BC-10] 전날 실행을 놓쳐 출발일이 오늘이 된 모집 중 베이스캠프도 처리한다")
    void processesMissedRunWhenStartDateIsToday() {
        long basecampId = fixture.saveBasecampWithMembers("RECRUITING", 1);
        clock.setInstant(START_DATE.atTime(10, 0).atZone(KST).toInstant());

        autoTransitionService.run();

        assertThat(fixture.statusOf(basecampId)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("[F-14][BC-10] 이미 확정, 취소, 완료된 베이스캠프는 바꾸지 않고 이벤트도 기록하지 않는다")
    void leavesFinishedBasecampsUntouched() {
        long confirmed = fixture.saveBasecampWithMembers("CONFIRMED", 1);
        long canceled = fixture.saveBasecamp("CANCELED");
        long completed = fixture.saveBasecampWithMembers("COMPLETED", 1);
        clock.setInstant(DAY_BEFORE_DEPARTURE);

        BasecampAutoTransitionService.Result result = autoTransitionService.run();

        assertThat(result.departureProcessed()).isZero();
        assertThat(fixture.statusOf(confirmed)).isEqualTo("CONFIRMED");
        assertThat(fixture.statusOf(canceled)).isEqualTo("CANCELED");
        assertThat(fixture.statusOf(completed)).isEqualTo("COMPLETED");
        assertThat(fixture.totalEventCount()).isZero();
    }

    @Test
    @DisplayName("[F-14][BC-10] 종료일 당일에는 완료하지 않고, 종료일 다음 날 0시(KST)가 되면 완료하며 탈퇴한 멤버를 뺀 ACTIVE 멤버에게 알린다")
    void completesFromDayAfterEndDate() {
        long basecampId = fixture.saveBasecampWithMembers("CONFIRMED", 1);
        fixture.insertMember(basecampId, fixture.saveVerifiedMember(), "MEMBER", "LEFT");
        long applicationId = fixture.insertPendingApplication(basecampId);

        clock.setInstant(DAY_AFTER_END.minusSeconds(1));
        autoTransitionService.run();
        assertThat(fixture.statusOf(basecampId)).isEqualTo("CONFIRMED");

        clock.setInstant(DAY_AFTER_END);
        BasecampAutoTransitionService.Result result = autoTransitionService.run();

        assertThat(result.completed()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT status, completed_at FROM basecamp WHERE id = ?", basecampId))
                .containsEntry("status", "COMPLETED")
                .doesNotContainEntry("completed_at", null);
        assertThat(fixture.applicationStatusOf(applicationId)).isEqualTo("EXPIRED");
        assertThat(fixture.payloadMemberIdsOf(BasecampTransitionEvents.COMPLETED_EVENT_TYPE, basecampId))
                .containsExactly(fixture.leaderIdOf(basecampId), fixture.firstMemberIdOf(basecampId));
    }

    @Test
    @DisplayName("[F-14][BC-10] 두 번 실행해도 전이와 이벤트는 한 번씩만 일어난다")
    void runningTwiceTransitionsOnce() {
        long confirmedTarget = fixture.saveBasecampWithMembers("RECRUITING", 1);
        long canceledTarget = fixture.saveBasecamp("RECRUITING");
        clock.setInstant(DAY_BEFORE_DEPARTURE);

        autoTransitionService.run();
        BasecampAutoTransitionService.Result second = autoTransitionService.run();

        assertThat(second.departureProcessed()).isZero();
        assertThat(fixture.eventCount(BasecampTransitionEvents.CONFIRMED_EVENT_TYPE, confirmedTarget))
                .isEqualTo(1);
        assertThat(fixture.eventCount(BasecampTransitionEvents.CANCELED_EVENT_TYPE, canceledTarget))
                .isEqualTo(1);
        assertThat(fixture.totalEventCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("[F-14][BC-10] 출발일이 내일보다 늦은 베이스캠프와 종료일이 오늘 이후인 확정 베이스캠프는 처리하지 않는다")
    void ignoresBasecampsNotDue() {
        long farStart =
                fixture.saveBasecamp("RECRUITING", null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21));
        long stillRunning = fixture.saveBasecampWithMembers("CONFIRMED", 1);
        clock.setInstant(END_DATE.atTime(12, 0).atZone(KST).toInstant());

        autoTransitionService.run();

        assertThat(fixture.statusOf(farStart)).isEqualTo("RECRUITING");
        assertThat(fixture.statusOf(stillRunning)).isEqualTo("CONFIRMED");
        assertThat(fixture.totalEventCount()).isZero();
    }
}
