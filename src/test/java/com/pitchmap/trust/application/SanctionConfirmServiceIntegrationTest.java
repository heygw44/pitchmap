package com.pitchmap.trust.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.domain.SanctionType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SanctionConfirmServiceIntegrationTest {

    @Autowired
    private SanctionConfirmService sanctionConfirmService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    private CompanionReviewFixture fixture;
    private long adminId;
    private long targetId;

    @BeforeEach
    void setUp() {
        fixture = new CompanionReviewFixture(jdbc, memberRepository);
        adminId = fixture.saveVerifiedMember(TestSequence.nickname());
        targetId = fixture.saveVerifiedMember(TestSequence.nickname());
    }

    @Test
    @DisplayName("[SN-10][SN-13] 7일 정지를 확정하면 제재 기록, 회원 정지, 베이스캠프 정리 이벤트가 하나씩 남는다")
    void sevenDaySuspensionRecordsSanctionSuspensionAndEvent() {
        confirm(SanctionType.WARNING);

        SanctionConfirmResult result = confirm(SanctionType.SUSPEND_7D);

        assertThat(result.suspended()).isTrue();
        Map<String, Object> sanction = jdbc.queryForMap(
                "SELECT type, level, status, ends_at, created_by, reason FROM sanction WHERE id = ?",
                result.sanctionId());
        assertThat(sanction.get("type")).isEqualTo("SUSPEND_7D");
        assertThat(((Number) sanction.get("level")).intValue()).isEqualTo(2);
        assertThat(sanction.get("status")).isEqualTo("ACTIVE");
        assertThat(sanction.get("created_by")).isEqualTo(adminId);
        assertThat(sanction.get("ends_at"))
                .isEqualTo(LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(7)), ZoneOffset.UTC));
        Map<String, Object> member =
                jdbc.queryForMap("SELECT status, suspended_until FROM member WHERE id = ?", targetId);
        assertThat(member.get("status")).isEqualTo("SUSPENDED");
        assertThat(member.get("suspended_until")).isEqualTo(sanction.get("ends_at"));
        Map<String, Object> event = jdbc.queryForMap(
                "SELECT status, aggregate_type, aggregate_id FROM outbox_event WHERE event_type = ?",
                SanctionEvents.BASECAMP_CLEANUP_EVENT_TYPE);
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("aggregate_type")).isEqualTo("MEMBER");
        assertThat(event.get("aggregate_id")).isEqualTo(targetId);
        // 경고와 7일 정지의 제재 알림 이벤트 2건과 정지의 베이스캠프 정리 이벤트 1건이다.
        assertThat(count("outbox_event")).isEqualTo(3);
        assertThat(countEvents(SanctionEvents.BASECAMP_CLEANUP_EVENT_TYPE)).isEqualTo(1);
        assertThat(countEvents(SanctionEvents.NOTIFICATION_EVENT_TYPE)).isEqualTo(2);
    }

    @Test
    @DisplayName("[SN-10] 경고는 제재 기록과 제재 알림 이벤트만 남기고 회원 상태는 그대로이며 베이스캠프 정리 이벤트는 없다")
    void warningOnlyRecordsSanction() {
        SanctionConfirmResult result = confirm(SanctionType.WARNING);

        assertThat(result.suspended()).isFalse();
        assertThat(jdbc.queryForObject("SELECT ends_at FROM sanction", LocalDateTime.class))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM member WHERE id = ?", String.class, targetId))
                .isEqualTo("UNVERIFIED");
        assertThat(count("outbox_event")).isEqualTo(1);
        assertThat(countEvents(SanctionEvents.BASECAMP_CLEANUP_EVENT_TYPE)).isZero();
        assertThat(countEvents(SanctionEvents.NOTIFICATION_EVENT_TYPE)).isEqualTo(1);
    }

    @Test
    @DisplayName("[SN-10] 제재는 경고, 7일, 30일, 영구 순서로 단계가 오르고 영구 정지는 종료 시각이 없다")
    void levelsRiseInOrder() {
        confirm(SanctionType.WARNING);
        confirm(SanctionType.SUSPEND_7D);
        confirm(SanctionType.SUSPEND_30D);
        confirm(SanctionType.PERMANENT);

        assertThat(jdbc.queryForList("SELECT CONCAT(type, ':', level) FROM sanction ORDER BY id", String.class))
                .containsExactly("WARNING:1", "SUSPEND_7D:2", "SUSPEND_30D:3", "PERMANENT:4");
        Map<String, Object> member =
                jdbc.queryForMap("SELECT status, suspended_until FROM member WHERE id = ?", targetId);
        assertThat(member.get("status")).isEqualTo("SUSPENDED");
        assertThat(member.get("suspended_until")).isNull();
    }

    @Test
    @DisplayName("[SN-10] 계산된 다음 단계와 다른 종류를 요청하면 INVALID_INPUT이고 아무것도 저장하지 않는다")
    void rejectsWrongType() {
        assertThatThrownBy(() -> confirm(SanctionType.SUSPEND_30D))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> confirm(SanctionType.TEMPORARY_72H)).isInstanceOf(BusinessException.class);

        assertThat(count("sanction")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    @DisplayName("[SN-11] 심각한 위반이면 첫 제재로 영구 정지를 곧바로 내릴 수 있다")
    void permanentCanSkipLevels() {
        confirm(SanctionType.PERMANENT);

        assertThat(jdbc.queryForObject("SELECT level FROM sanction", Integer.class))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("[SN-10] 다음 단계 계산에서 해제된 제재와 임시 정지는 빼고, 기간이 끝난 제재는 넣는다")
    void nextLevelIgnoresLiftedAndTemporaryButCountsExpired() {
        insertSanction("TEMPORARY_72H", null, "ACTIVE");
        insertSanction("SUSPEND_7D", 2, "LIFTED");
        confirm(SanctionType.WARNING);
        insertSanction("SUSPEND_7D", 2, "EXPIRED");

        assertThatThrownBy(() -> confirm(SanctionType.SUSPEND_7D)).isInstanceOf(BusinessException.class);
        confirm(SanctionType.SUSPEND_30D);

        assertThat(jdbc.queryForObject("SELECT MAX(level) FROM sanction WHERE type = 'SUSPEND_30D'", Integer.class))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("[SN-05] 이미 더 늦게 끝나는 정지가 있으면 7일 정지가 종료 시각을 줄이지 않는다")
    void keepsLaterSuspensionEnd() {
        confirm(SanctionType.WARNING);
        jdbc.update(
                "UPDATE member SET status = 'SUSPENDED', suspended_until = ? WHERE id = ?",
                LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(20)), ZoneOffset.UTC),
                targetId);

        confirm(SanctionType.SUSPEND_7D);

        assertThat(jdbc.queryForObject(
                        "SELECT suspended_until FROM member WHERE id = ?", LocalDateTime.class, targetId))
                .isEqualTo(LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(20)), ZoneOffset.UTC));
    }

    private SanctionConfirmResult confirm(SanctionType type) {
        return sanctionConfirmService.confirm(new SanctionConfirmCommand(targetId, null, type, "반복 위반", adminId));
    }

    private void insertSanction(String type, Integer level, String status) {
        jdbc.update(
                "INSERT INTO sanction (member_id, type, level, reason, starts_at, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, '테스트', NOW(6), ?, NOW(6), NOW(6))",
                targetId,
                type,
                level,
                status);
    }

    private int countEvents(String eventType) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_type = ?", Integer.class, eventType);
        return count == null ? 0 : count;
    }

    private int count(String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
