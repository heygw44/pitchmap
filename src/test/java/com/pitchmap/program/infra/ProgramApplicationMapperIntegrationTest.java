package com.pitchmap.program.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.program.application.ProgramApplyFixture;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ProgramApplicationMapperIntegrationTest {

    private static final Instant NOW = MutableClock.DEFAULT_INSTANT;

    @Autowired
    private ProgramApplicationMapper mapper;

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
    @DisplayName("[F-18][NFR-02] 자리가 있으면 insertIfSeatAvailable이 1을 돌려주고 신청 ID를 채우며, 정원이 차면 0을 돌려주고 행을 넣지 않는다")
    void insertIfSeatAvailableReturnsAffectedRows() {
        // given
        long programId = fixture.saveProgram(1, false);
        SeatClaim first = claim(programId, fixture.saveMember());
        SeatClaim second = claim(programId, fixture.saveMember());

        // when
        int firstRows = mapper.insertIfSeatAvailable(first);
        int secondRows = mapper.insertIfSeatAvailable(second);

        // then
        assertThat(firstRows).isEqualTo(1);
        assertThat(first.getId()).isNotNull();
        assertThat(secondRows).isZero();
        assertThat(second.getId()).isNull();
        assertThat(fixture.activeCount(programId)).isEqualTo(1);
    }

    @Test
    @DisplayName("[F-18][NFR-02] 취소·만료된 신청은 정원을 차지하지 않고, 확정 신청은 차지한다")
    void onlyPendingPaymentAndConfirmedOccupySeats() {
        // given
        long programId = fixture.saveProgram(2, false);
        fixture.saveApplication(programId, fixture.saveMember(), "CANCELED");
        fixture.saveApplication(programId, fixture.saveMember(), "EXPIRED");
        fixture.saveApplication(programId, fixture.saveMember(), "CONFIRMED");

        // when, then
        assertThat(mapper.insertIfSeatAvailable(claim(programId, fixture.saveMember())))
                .isEqualTo(1);
        assertThat(mapper.insertIfSeatAvailable(claim(programId, fixture.saveMember())))
                .isZero();
    }

    @Test
    @DisplayName("[F-18][PG-03] existsActive는 결제 대기·확정 신청만 true이다")
    void existsActiveChecksActiveStatuses() {
        // given
        long programId = fixture.saveProgram(5, false);
        long pending = fixture.saveMember();
        long confirmed = fixture.saveMember();
        long canceled = fixture.saveMember();
        long expired = fixture.saveMember();
        long none = fixture.saveMember();
        fixture.saveApplication(programId, pending, "PENDING_PAYMENT");
        fixture.saveApplication(programId, confirmed, "CONFIRMED");
        fixture.saveApplication(programId, canceled, "CANCELED");
        fixture.saveApplication(programId, expired, "EXPIRED");

        // then
        assertThat(mapper.existsActive(programId, pending)).isTrue();
        assertThat(mapper.existsActive(programId, confirmed)).isTrue();
        assertThat(mapper.existsActive(programId, canceled)).isFalse();
        assertThat(mapper.existsActive(programId, expired)).isFalse();
        assertThat(mapper.existsActive(programId, none)).isFalse();
    }

    @Test
    @DisplayName("[F-18][PG-03] 같은 회원의 활성 신청 두 건을 직접 넣으면 UNIQUE 위반이고, 취소된 신청은 여러 건이어도 허용한다")
    void uniqueConstraintAllowsOnlyOneActiveApplication() {
        // given
        long programId = fixture.saveProgram(5, false);
        long memberId = fixture.saveMember();
        fixture.saveApplication(programId, memberId, "PENDING_PAYMENT");

        // then
        assertThatThrownBy(() -> fixture.saveApplication(programId, memberId, "CONFIRMED"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatCode(() -> {
                    fixture.saveApplication(programId, memberId, "CANCELED");
                    fixture.saveApplication(programId, memberId, "CANCELED");
                    fixture.saveApplication(programId, memberId, "EXPIRED");
                })
                .doesNotThrowAnyException();
        // 활성 신청이 취소로 바뀌면 같은 회원이 다시 활성 신청을 가질 수 있다.
        jdbc.update(
                "UPDATE program_application SET status = 'CANCELED' WHERE program_id = ? AND member_id = ?",
                programId,
                memberId);
        assertThatCode(() -> fixture.saveApplication(programId, memberId, "PENDING_PAYMENT"))
                .doesNotThrowAnyException();
    }

    private static SeatClaim claim(long programId, long memberId) {
        return new SeatClaim(programId, memberId, NOW.plus(Duration.ofMinutes(15)), NOW);
    }
}
