package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.pitchmap.basecamp.application.BasecampSanctionCleanupService;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxProperties;
import com.pitchmap.notification.application.OutboxPublisher;
import com.pitchmap.program.application.ProgramSanctionCleanupService;
import com.pitchmap.trust.domain.SanctionType;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@IntegrationTest
class SanctionFollowUpReliabilityIntegrationTest {

    @MockitoBean
    private BasecampSanctionCleanupService cleanupService;

    @MockitoBean
    private ProgramSanctionCleanupService programCleanupService;

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxProperties outboxProperties;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void resetMock() {
        reset(cleanupService, programCleanupService);
    }

    @Test
    @DisplayName("[SN-13][NFR-04] 베이스캠프 정리가 실패해도 제재와 회원 정지는 유지되고, 아웃박스 행은 PENDING으로 남아 재시도 뒤 처리된다")
    void followUpFailureKeepsSanctionAndRetriesEvent() {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        long targetId = fixture.saveVerifiedMember(TestSequence.nickname());
        doThrow(new IllegalStateException("simulated cleanup failure"))
                .when(cleanupService)
                .cleanUp(targetId);

        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(targetId, null, SanctionType.PERMANENT, "금전 요구", adminId));
        int processed = outboxPublisher.publishPending();

        // 베이스캠프 정리·행사 신청 정리·제재 알림 이벤트를 모두 집는다. 베이스캠프 정리만 실패한다.
        assertThat(processed).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sanction WHERE member_id = ?", Integer.class, targetId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, targetId))
                .isEqualTo("SUSPENDED");
        Map<String, Object> event = jdbc.queryForMap(
                "SELECT status, attempt_count FROM outbox_event WHERE event_type = ?",
                SanctionEvents.BASECAMP_CLEANUP_EVENT_TYPE);
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("attempt_count")).isEqualTo(1);

        reset(cleanupService);
        clock.advance(outboxProperties.retryDelay());
        outboxPublisher.publishPending();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM outbox_event WHERE event_type = ?",
                        String.class,
                        SanctionEvents.BASECAMP_CLEANUP_EVENT_TYPE))
                .isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("[SN-14][NFR-04] 행사 신청 정리가 실패해도 제재와 회원 정지는 유지되고, 아웃박스 행은 PENDING으로 남아 재시도 뒤 처리된다")
    void programCleanupFailureKeepsSanctionAndRetriesEvent() {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        long adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        long targetId = fixture.saveVerifiedMember(TestSequence.nickname());
        doThrow(new IllegalStateException("simulated program cleanup failure"))
                .when(programCleanupService)
                .cleanUp(targetId);

        sanctionConfirmService.confirm(
                new SanctionConfirmCommand(targetId, null, SanctionType.PERMANENT, "금전 요구", adminId));
        int processed = outboxPublisher.publishPending();

        assertThat(processed).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sanction WHERE member_id = ?", Integer.class, targetId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, targetId))
                .isEqualTo("SUSPENDED");
        Map<String, Object> event = jdbc.queryForMap(
                "SELECT status, attempt_count FROM outbox_event WHERE event_type = ?",
                SanctionEvents.PROGRAM_CLEANUP_EVENT_TYPE);
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("attempt_count")).isEqualTo(1);

        reset(programCleanupService);
        clock.advance(outboxProperties.retryDelay());
        outboxPublisher.publishPending();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM outbox_event WHERE event_type = ?",
                        String.class,
                        SanctionEvents.PROGRAM_CLEANUP_EVENT_TYPE))
                .isEqualTo("PUBLISHED");
    }
}
