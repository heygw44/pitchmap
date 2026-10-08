package com.pitchmap.basecamp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.error.BusinessException;
import com.pitchmap.common.error.CommonErrorCode;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.common.testsupport.TestSequence;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.trust.application.CompanionReviewFixture;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BasecampOpenSanctionIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Autowired
    private BasecampOpenService basecampOpenService;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long leaderId;
    private long spotId;

    @BeforeEach
    void setUp() {
        CompanionReviewFixture fixture = new CompanionReviewFixture(jdbc, memberRepository);
        leaderId = fixture.saveVerifiedMember(TestSequence.nickname());
        jdbc.update("INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                + " VALUES ('BAKJI', '개머리언덕', ST_GeomFromText('POINT(37.25 127.25)', 4326), 60, 127, 'ACTIVE',"
                + " NOW(6), NOW(6))");
        spotId = jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
    }

    @Test
    @DisplayName("[BC-01][SN-10] 지금 적용 중인 정지가 있는 회원이 베이스캠프를 열면 ACCESS_DENIED이고 베이스캠프가 만들어지지 않는다")
    void activeSuspensionBlocksOpening() {
        insertSanction("SUSPEND_7D", "ACTIVE", NOW.plus(Duration.ofDays(3)));

        assertThatThrownBy(this::open)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM basecamp", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("[BC-01][SN-10] 영구 정지와 임시 정지가 적용 중이어도 베이스캠프를 열 수 없다")
    void permanentAndTemporarySuspensionBlockOpening() {
        insertSanction("PERMANENT", "ACTIVE", null);
        assertThatThrownBy(this::open).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("[BC-01][SN-10] 경고, 기간이 끝난 정지, 해제된 정지는 베이스캠프 열기를 막지 않는다")
    void warningExpiredAndLiftedDoNotBlock() {
        insertSanction("WARNING", "ACTIVE", null);
        insertSanction("SUSPEND_7D", "EXPIRED", NOW.minus(Duration.ofDays(1)));
        insertSanction("SUSPEND_30D", "LIFTED", NOW.plus(Duration.ofDays(20)));
        // 종료 시각은 지났지만 만료 작업이 아직 돌지 않은 정지도 이미 끝난 정지다.
        insertSanction("SUSPEND_7D", "ACTIVE", NOW.minusSeconds(1));

        BasecampOpenResult result = open();

        assertThat(result.status()).isEqualTo("RECRUITING");
    }

    private BasecampOpenResult open() {
        LocalDate start = LocalDate.of(2026, 10, 20);
        return basecampOpenService.open(
                leaderId, new BasecampOpenCommand(spotId, "굴업도 1박", "함께 가요", start, start.plusDays(2), 4, null));
    }

    private void insertSanction(String type, String status, Instant endsAt) {
        jdbc.update(
                "INSERT INTO sanction (member_id, type, level, reason, starts_at, ends_at, status, created_at, updated_at)"
                        + " VALUES (?, ?, 1, '테스트', ?, ?, ?, NOW(6), NOW(6))",
                leaderId,
                type,
                LocalDateTime.ofInstant(NOW.minus(Duration.ofDays(10)), ZoneOffset.UTC),
                endsAt == null ? null : LocalDateTime.ofInstant(endsAt, ZoneOffset.UTC),
                status);
    }
}
