package com.pitchmap.basecamp.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.pitchmap.basecamp.domain.RemovalCause;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.notification.application.OutboxPublisher;
import com.pitchmap.trust.application.CompanionReviewFixture;
import com.pitchmap.trust.application.WithdrawalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class WithdrawalBasecampCleanupIntegrationTest {

    private static final String PASSWORD = "Valid-pass1";

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private BasecampSanctionCleanupService cleanupService;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
    }

    @Test
    @DisplayName("[F-12][PV-02] 캠프 리더가 탈퇴하고 발행기가 돌면 베이스캠프는 LEADER_WITHDRAWN으로 취소되고 남은 멤버에게 알리는 이벤트가 기록된다")
    void leaderWithdrawalCancelsBasecamp() {
        long leader = saveMemberWithPassword();
        long basecamp = ledBasecamp(leader);
        long other = joinMember(basecamp);
        long applicant = fixture.saveVerifiedMember(TestSequence.nickname());
        fixture.insertApplication(basecamp, applicant, "PENDING");

        withdrawalService.withdraw(leader, PASSWORD);
        assertThat(basecampStatus(basecamp)).isEqualTo("RECRUITING");
        outboxPublisher.publishPending();

        assertThat(jdbc.queryForObject(
                        "SELECT CONCAT(status, ':', cancel_reason) FROM basecamp WHERE id = ?", String.class, basecamp))
                .isEqualTo("CANCELED:LEADER_WITHDRAWN");
        assertThat(memberIdsOf("BASECAMP_CANCELED", basecamp)).containsExactly(other);
        assertThat(eventStatus("WITHDRAWAL_BASECAMP_CLEANUP", leader)).isEqualTo("PUBLISHED");
        assertThat(eventStatus("WITHDRAWAL_PROGRAM_CLEANUP", leader)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("[F-14][PV-02] 멤버가 탈퇴하면 LEFT가 되고 임박 탈퇴로 세지 않으며, 자동 마감한 베이스캠프는 다시 모집한다")
    void memberWithdrawalLeavesWithoutEarlyLeave() {
        long member = saveMemberWithPassword();
        long confirmed = fixture.saveBasecamp("CONFIRMED", null);
        fixture.insertMember(confirmed, member, "MEMBER", "ACTIVE");
        long autoClosed = fixture.saveBasecamp("CLOSED", null);
        jdbc.update("UPDATE basecamp SET closed_reason = 'AUTO_FULL' WHERE id = ?", autoClosed);
        fixture.insertMember(autoClosed, member, "MEMBER", "ACTIVE");

        withdrawalService.withdraw(member, PASSWORD);
        outboxPublisher.publishPending();

        assertThat(memberRow(confirmed, member)).isEqualTo("LEFT:0");
        assertThat(memberRow(autoClosed, member)).isEqualTo("LEFT:0");
        assertThat(basecampStatus(confirmed)).isEqualTo("CONFIRMED");
        assertThat(basecampStatus(autoClosed)).isEqualTo("RECRUITING");
        assertThat(memberIdsOf("BASECAMP_MEMBER_CHANGED", confirmed)).containsExactly(fixture.leaderIdOf(confirmed));
    }

    @Test
    @DisplayName("[F-13][PV-02] 대기 중인 신청자가 탈퇴하면 신청이 CANCELED가 되고 베이스캠프는 그대로다")
    void applicantWithdrawalCancelsApplication() {
        long applicant = saveMemberWithPassword();
        long basecamp = fixture.saveBasecamp("RECRUITING", null);
        fixture.insertApplication(basecamp, applicant, "PENDING");
        long unrelated = fixture.saveBasecamp("RECRUITING", null);

        withdrawalService.withdraw(applicant, PASSWORD);
        outboxPublisher.publishPending();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM basecamp_application WHERE basecamp_id = ? AND applicant_id = ?",
                        String.class,
                        basecamp,
                        applicant))
                .isEqualTo("CANCELED");
        assertThat(basecampStatus(basecamp)).isEqualTo("RECRUITING");
        assertThat(basecampStatus(unrelated)).isEqualTo("RECRUITING");
    }

    @Test
    @DisplayName("[F-12][NFR-04] 같은 정리를 다시 실행해도 베이스캠프와 이벤트가 바뀌지 않는다")
    void rerunChangesNothing() {
        long leader = saveMemberWithPassword();
        long ledBasecamp = ledBasecamp(leader);
        joinMember(ledBasecamp);
        long member = saveMemberWithPassword();
        long joined = fixture.saveBasecamp("CONFIRMED", null);
        fixture.insertMember(joined, member, "MEMBER", "ACTIVE");
        withdrawalService.withdraw(leader, PASSWORD);
        withdrawalService.withdraw(member, PASSWORD);
        outboxPublisher.publishPending();
        int eventsBefore = count("outbox_event WHERE event_type LIKE 'BASECAMP_%'");
        Object updatedAtBefore =
                jdbc.queryForObject("SELECT updated_at FROM basecamp WHERE id = ?", Object.class, ledBasecamp);

        cleanupService.cleanUp(leader, RemovalCause.WITHDRAWAL);
        cleanupService.cleanUp(member, RemovalCause.WITHDRAWAL);

        assertThat(count("outbox_event WHERE event_type LIKE 'BASECAMP_%'")).isEqualTo(eventsBefore);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM basecamp WHERE id = ?", Object.class, ledBasecamp))
                .isEqualTo(updatedAtBefore);
        assertThat(memberRow(joined, member)).isEqualTo("LEFT:0");
    }

    private long saveMemberWithPassword() {
        long memberId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update(
                "UPDATE member SET password_hash = ?, status = 'ACTIVE', email_verified_at = NOW(6) WHERE id = ?",
                passwordEncoder.encode(PASSWORD),
                memberId);
        return memberId;
    }

    private long ledBasecamp(long leaderId) {
        long basecampId = fixture.saveBasecamp("RECRUITING", null);
        jdbc.update(
                "UPDATE basecamp_member SET member_id = ? WHERE basecamp_id = ? AND role = 'LEADER'",
                leaderId,
                basecampId);
        jdbc.update("UPDATE basecamp SET leader_id = ? WHERE id = ?", leaderId, basecampId);
        return basecampId;
    }

    private long joinMember(long basecampId) {
        long memberId = fixture.saveVerifiedMember(TestSequence.nickname());
        fixture.insertMember(basecampId, memberId, "MEMBER", "ACTIVE");
        return memberId;
    }

    private String basecampStatus(long basecampId) {
        return jdbc.queryForObject("SELECT status FROM basecamp WHERE id = ?", String.class, basecampId);
    }

    private String memberRow(long basecampId, long memberId) {
        return jdbc.queryForObject(
                "SELECT CONCAT(status, ':', early_leave) FROM basecamp_member WHERE basecamp_id = ? AND member_id = ?",
                String.class,
                basecampId,
                memberId);
    }

    private String eventStatus(String eventType, long memberId) {
        return jdbc.queryForObject(
                "SELECT status FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                String.class,
                eventType,
                memberId);
    }

    private List<Long> memberIdsOf(String eventType, long aggregateId) {
        List<String> payloads = jdbc.queryForList(
                "SELECT payload FROM outbox_event WHERE event_type = ? AND aggregate_id = ?",
                String.class,
                eventType,
                aggregateId);
        assertThat(payloads).hasSize(1);
        List<Number> ids = JsonPath.read(payloads.get(0), "$.memberIds");
        return ids.stream().map(Number::longValue).toList();
    }

    private int count(String fromAndWhere) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + fromAndWhere, Integer.class);
        return count == null ? 0 : count;
    }
}
