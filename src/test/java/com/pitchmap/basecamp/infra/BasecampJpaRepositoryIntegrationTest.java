package com.pitchmap.basecamp.infra;

import static com.pitchmap.basecamp.domain.BasecampBuilder.NOW;
import static com.pitchmap.basecamp.domain.BasecampBuilder.details;
import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;

import com.pitchmap.basecamp.domain.Basecamp;
import com.pitchmap.basecamp.domain.BasecampDetails;
import com.pitchmap.basecamp.domain.BasecampMember;
import com.pitchmap.basecamp.domain.BasecampMemberRole;
import com.pitchmap.basecamp.domain.BasecampOpenPolicy;
import com.pitchmap.basecamp.domain.BasecampStatus;
import com.pitchmap.basecamp.domain.JoinCondition;
import com.pitchmap.basecamp.domain.JoinGender;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.common.testsupport.MutableClock;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.spot.domain.GeoPoint;
import com.pitchmap.spot.domain.ParkAreaJudgement;
import com.pitchmap.spot.domain.Spot;
import com.pitchmap.spot.infra.SpotJpaRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class BasecampJpaRepositoryIntegrationTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 20);

    @Autowired
    private BasecampJpaRepository basecampRepository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private SpotJpaRepository spotRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("[F-12] 저장한 베이스캠프를 다시 읽으면 캠프 리더 멤버 행과 합류 조건이 그대로 매핑된다")
    void savedBasecampIsReadBackWithMembersAndJoinCondition() {
        // given
        Member leader = saveMember();
        long spotId = saveSpot();
        JoinCondition condition = JoinCondition.none()
                .withMinTrustLevel(2)
                .withAgeGroupRange(20, 40)
                .withSameGenderOnly(JoinGender.FEMALE);
        BasecampDetails details = new BasecampDetails(
                "북한산 백패킹",
                "함께 가요",
                START_DATE,
                START_DATE.plusDays(2),
                details(4, START_DATE).capacity(),
                condition);

        // when
        Long id = basecampRepository
                .saveAndFlush(Basecamp.open(leader.getId(), spotId, details, NOW))
                .getId();
        // members는 지연 로딩이라 같은 트랜잭션 안에서 읽는다.
        transactionTemplate.executeWithoutResult(status -> {
            Basecamp found = basecampRepository.findById(id).orElseThrow();

            // then
            assertThat(found.getStatus()).isEqualTo(BasecampStatus.RECRUITING);
            assertThat(found.getLeaderId()).isEqualTo(leader.getId());
            assertThat(found.getSpotId()).isEqualTo(spotId);
            assertThat(found.getCapacity()).isEqualTo(details.capacity());
            assertThat(found.getJoinCondition()).isEqualTo(condition);
            assertThat(found.getMembers()).hasSize(1);
            BasecampMember member = found.getMembers().get(0);
            assertThat(member.getMemberId()).isEqualTo(leader.getId());
            assertThat(member.getRole()).isEqualTo(BasecampMemberRole.LEADER);
            assertThat(found.headcount()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("[F-12][BC-01] 개수는 지정한 회원의 베이스캠프 중 지정한 상태만 센다")
    void countsOnlyGivenLeaderAndStatuses() {
        // given
        Member leader = saveMember();
        Member other = saveMember();
        long spotId = saveSpot();
        insert(leader, spotId, "RECRUITING");
        insert(leader, spotId, "CLOSED");
        insert(leader, spotId, "CONFIRMED");
        insert(leader, spotId, "CANCELED");
        insert(other, spotId, "RECRUITING");

        // when
        long counted =
                basecampRepository.countByLeaderIdAndStatusIn(leader.getId(), BasecampOpenPolicy.OPEN_COUNTED_STATUSES);

        // then
        assertThat(counted).isEqualTo(2);
    }

    private void insert(Member leader, long spotId, String status) {
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '기존', '기존', '2026-10-20', '2026-10-22', 4, ?,"
                        + " '2026-10-05 03:00:00', '2026-10-05 03:00:00')",
                leader.getId(),
                spotId,
                status);
    }

    private Member saveMember() {
        return memberRepository.saveAndFlush(aMember().build());
    }

    private long saveSpot() {
        Spot spot = Spot.bakji(
                "능선 끝 평지",
                new GeoPoint(37.25, 127.25),
                ParkAreaJudgement.outside(MutableClock.DEFAULT_INSTANT),
                MutableClock.DEFAULT_INSTANT);
        return spotRepository.save(spot).getId();
    }
}
