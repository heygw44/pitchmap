package com.pitchmap.basecamp.infra;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pitchmap.basecamp.domain.BasecampApplicationStatus;
import com.pitchmap.basecamp.domain.BasecampMemberStatus;
import com.pitchmap.basecamp.domain.BasecampStatus;
import com.pitchmap.basecamp.domain.JoinGender;
import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.domain.Member;
import com.pitchmap.member.infra.MemberJpaRepository;
import com.pitchmap.spot.application.SpotSearchBox;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BasecampSearchMapperIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    // 영역 검사용 화면 영역이다. 남서쪽 꼭짓점은 (37.0, 127.0), 북동쪽 꼭짓점은 (38.0, 128.0)이다.
    private static final BasecampSearchCondition.Area AREA = new BasecampSearchCondition.Area(37.0, 127.0, 38.0, 128.0);

    // 반경 검사용 중심은 서울시청이다. 아래 장소의 거리는 MySQL이 쓰는 구 위에서 계산한 값이다.
    private static final Coordinate CENTER = new Coordinate(37.5665, 126.978);
    private static final Coordinate NORTH_9_8_KM = new Coordinate(37.6546, 126.978);
    private static final Coordinate EAST_10_2_KM = new Coordinate(37.5665, 127.0936);

    @Autowired
    private BasecampSearchMapper mapper;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("[F-12] 영역 조회는 경계선 위의 장소를 포함하고 영역 밖의 장소는 뺀다")
    void areaIncludesBoundaryAndExcludesOutside() {
        // given
        Member leader = saveMember();
        long southWest =
                insertBasecamp(leader, insertSpot("남서 꼭짓점", new Coordinate(37.0, 127.0)), "RECRUITING", DAY, 4);
        long northEast =
                insertBasecamp(leader, insertSpot("북동 꼭짓점", new Coordinate(38.0, 128.0)), "RECRUITING", DAY, 4);
        long inside = insertBasecamp(leader, insertSpot("안쪽", new Coordinate(37.5, 127.5)), "RECRUITING", DAY, 4);
        insertBasecamp(leader, insertSpot("남쪽 바깥", new Coordinate(36.99, 127.5)), "RECRUITING", DAY, 4);
        insertBasecamp(leader, insertSpot("동쪽 바깥", new Coordinate(37.5, 128.01)), "RECRUITING", DAY, 4);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(areaCondition(AREA), 0, 20);

        // then
        assertThat(rows)
                .extracting(BasecampSearchRow::basecampId)
                .containsExactlyInAnyOrder(southWest, northEast, inside);
    }

    @Test
    @DisplayName("[F-12] 영역 조회는 위도와 경도를 서로 바꿔 읽지 않는다")
    void areaDoesNotSwapLatitudeAndLongitude() {
        // given: 위도와 경도가 비슷한 곳에서 두 값을 맞바꾼 두 장소를 둔다. 조회 SQL이 좌표 순서를 바꿔 읽으면 결과가 달라진다.
        Member leader = saveMember();
        long expected = insertBasecamp(leader, insertSpot("제자리", new Coordinate(37.5, 37.6)), "RECRUITING", DAY, 4);
        insertBasecamp(leader, insertSpot("맞바꾼 자리", new Coordinate(37.6, 37.5)), "RECRUITING", DAY, 4);
        BasecampSearchCondition.Area area = new BasecampSearchCondition.Area(37.4, 37.55, 37.55, 37.65);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(areaCondition(area), 0, 20);

        // then
        assertThat(rows).extracting(BasecampSearchRow::basecampId).containsExactly(expected);
        assertThat(rows.getFirst().spotLat()).isEqualTo(37.5);
        assertThat(rows.getFirst().spotLng()).isEqualTo(37.6);
    }

    @Test
    @DisplayName("[F-12] 반경 검색은 반경 경계 안쪽 장소를 찾고 경계 바로 밖의 장소는 뺀다")
    void radiusIncludesInsideAndExcludesJustOutside() {
        // given
        Member leader = saveMember();
        long center = insertBasecamp(leader, insertSpot("중심", CENTER), "RECRUITING", DAY, 4);
        long north = insertBasecamp(leader, insertSpot("북쪽 9.8km", NORTH_9_8_KM), "RECRUITING", DAY, 4);
        insertBasecamp(leader, insertSpot("동쪽 10.2km", EAST_10_2_KM), "RECRUITING", DAY, 4);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(radiusCondition(CENTER, 10), 0, 20);

        // then
        assertThat(rows).extracting(BasecampSearchRow::basecampId).containsExactlyInAnyOrder(center, north);
    }

    @Test
    @DisplayName("[F-12] 출발일 범위는 양 끝을 포함하고, 한쪽만 보내면 그쪽만 제한한다")
    void startDateRange() {
        // given
        Member leader = saveMember();
        long spotId = insertSpot("장소", new Coordinate(37.5, 127.5));
        long d20 = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        long d22 = insertBasecamp(leader, spotId, "RECRUITING", DAY.plusDays(2), 4);
        long d25 = insertBasecamp(leader, spotId, "RECRUITING", DAY.plusDays(5), 4);

        // when
        List<BasecampSearchRow> between = mapper.selectRecruiting(
                new BasecampSearchCondition(AREA, null, DAY.plusDays(2), DAY.plusDays(5), false), 0, 20);
        List<BasecampSearchRow> fromOnly =
                mapper.selectRecruiting(new BasecampSearchCondition(AREA, null, DAY.plusDays(1), null, false), 0, 20);
        List<BasecampSearchRow> toOnly =
                mapper.selectRecruiting(new BasecampSearchCondition(AREA, null, null, DAY.plusDays(2), false), 0, 20);

        // then
        assertThat(between).extracting(BasecampSearchRow::basecampId).containsExactly(d22, d25);
        assertThat(fromOnly).extracting(BasecampSearchRow::basecampId).containsExactly(d22, d25);
        assertThat(toOnly).extracting(BasecampSearchRow::basecampId).containsExactly(d20, d22);
    }

    @Test
    @DisplayName("[F-12] 남은 자리 조건은 ACTIVE 멤버 수만 세서 정원이 찬 베이스캠프를 뺀다")
    void hasVacancyCountsOnlyActiveMembers() {
        // given
        Member leader = saveMember();
        Member other = saveMember();
        Member leaver = saveMember();
        long spotId = insertSpot("장소", new Coordinate(37.5, 127.5));
        long full = insertBasecamp(leader, spotId, "RECRUITING", DAY, 2);
        insertMemberRow(full, other, "MEMBER", "ACTIVE");
        long vacant = insertBasecamp(leader, spotId, "RECRUITING", DAY, 2);
        insertMemberRow(vacant, leaver, "MEMBER", "LEFT");

        // when
        List<BasecampSearchRow> vacancyOnly =
                mapper.selectRecruiting(new BasecampSearchCondition(AREA, null, null, null, true), 0, 20);
        List<BasecampSearchRow> all =
                mapper.selectRecruiting(new BasecampSearchCondition(AREA, null, null, null, false), 0, 20);

        // then
        assertThat(vacancyOnly).extracting(BasecampSearchRow::basecampId).containsExactly(vacant);
        assertThat(all)
                .extracting(BasecampSearchRow::basecampId, BasecampSearchRow::headcount)
                .containsExactly(tuple(full, 2), tuple(vacant, 1));
    }

    @Test
    @DisplayName("[F-12] 모집 중인 베이스캠프만 찾고, 마감·확정·완료·취소와 숨긴 장소의 베이스캠프는 뺀다")
    void onlyRecruitingBasecampsOnActiveSpots() {
        // given
        Member leader = saveMember();
        long spotId = insertSpot("장소", new Coordinate(37.5, 127.5));
        long recruiting = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        for (BasecampStatus status : List.of(
                BasecampStatus.CLOSED, BasecampStatus.CONFIRMED, BasecampStatus.COMPLETED, BasecampStatus.CANCELED)) {
            insertBasecamp(leader, spotId, status.name(), DAY, 4);
        }
        long hiddenSpot = insertSpot("숨김 장소", new Coordinate(37.5, 127.5));
        jdbc.update("UPDATE spot SET status = 'HIDDEN' WHERE id = ?", hiddenSpot);
        insertBasecamp(leader, hiddenSpot, "RECRUITING", DAY, 4);

        // when
        List<BasecampSearchRow> rows = mapper.selectRecruiting(areaCondition(AREA), 0, 20);

        // then
        assertThat(rows).extracting(BasecampSearchRow::basecampId).containsExactly(recruiting);
    }

    @Test
    @DisplayName("[F-12] 출발일 순서로 정렬하고 출발일이 같으면 ID 순서이며, offset과 limit으로 페이지를 나눈다")
    void orderedByStartDateThenIdWithPaging() {
        // given
        Member leader = saveMember();
        long spotId = insertSpot("장소", new Coordinate(37.5, 127.5));
        long late = insertBasecamp(leader, spotId, "RECRUITING", DAY.plusDays(3), 4);
        long earlyFirst = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        long earlySecond = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);

        // when
        List<BasecampSearchRow> all = mapper.selectRecruiting(areaCondition(AREA), 0, 20);
        List<BasecampSearchRow> secondPage = mapper.selectRecruiting(areaCondition(AREA), 2, 2);

        // then
        assertThat(all).extracting(BasecampSearchRow::basecampId).containsExactly(earlyFirst, earlySecond, late);
        assertThat(secondPage).extracting(BasecampSearchRow::basecampId).containsExactly(late);
    }

    @Test
    @DisplayName("[F-12] 결과에 장소 요약과 합류 조건, 정원, 현재 인원이 담긴다")
    void rowCarriesSpotAndJoinCondition() {
        // given
        Member leader = saveMember();
        long spotId = insertSpot("능선 끝 평지", new Coordinate(37.5, 127.5));
        long id = insertBasecamp(leader, spotId, "RECRUITING", DAY, 5);
        jdbc.update(
                "UPDATE basecamp SET min_trust_level = 2, age_group_min = 20, age_group_max = 30, same_gender_only = TRUE,"
                        + " required_gender = 'FEMALE' WHERE id = ?",
                id);

        // when
        BasecampSearchRow row =
                mapper.selectRecruiting(areaCondition(AREA), 0, 20).getFirst();

        // then
        assertThat(row.basecampId()).isEqualTo(id);
        assertThat(row.title()).isEqualTo("기존");
        assertThat(row.spotId()).isEqualTo(spotId);
        assertThat(row.spotName()).isEqualTo("능선 끝 평지");
        assertThat(row.spotType()).isEqualTo("BAKJI");
        assertThat(row.spotLat()).isEqualTo(37.5);
        assertThat(row.spotLng()).isEqualTo(127.5);
        assertThat(row.startDate()).isEqualTo(DAY);
        assertThat(row.endDate()).isEqualTo(DAY.plusDays(2));
        assertThat(row.capacity()).isEqualTo(5);
        assertThat(row.headcount()).isEqualTo(1);
        assertThat(row.status()).isEqualTo(BasecampStatus.RECRUITING);
        assertThat(row.minTrustLevel()).isEqualTo(2);
        assertThat(row.ageGroupMin()).isEqualTo(20);
        assertThat(row.ageGroupMax()).isEqualTo(30);
        assertThat(row.sameGenderOnly()).isTrue();
        assertThat(row.requiredGender()).isEqualTo(JoinGender.FEMALE);
    }

    @Test
    @DisplayName("[F-12][BC-07] 회원의 멤버 행과 신청 행 상태를 베이스캠프별로 읽고, 둘 다 없는 베이스캠프는 읽지 않는다")
    void viewerHistoryReadsMemberAndApplicationStatus() {
        // given
        Member leader = saveMember();
        Member viewer = saveMember();
        long spotId = insertSpot("장소", new Coordinate(37.5, 127.5));
        long asLeader = insertBasecamp(viewer, spotId, "RECRUITING", DAY, 4);
        long pending = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        insertApplicationRow(pending, viewer, "PENDING");
        long kicked = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        insertMemberRow(kicked, viewer, "MEMBER", "KICKED");
        insertApplicationRow(kicked, viewer, "APPROVED");
        long unrelated = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);

        // when
        List<ViewerHistoryRow> rows =
                mapper.selectViewerHistory(viewer.getId(), List.of(asLeader, pending, kicked, unrelated));

        // then
        assertThat(rows)
                .extracting(
                        ViewerHistoryRow::basecampId,
                        ViewerHistoryRow::memberStatus,
                        ViewerHistoryRow::applicationStatus)
                .containsExactlyInAnyOrder(
                        tuple(asLeader, BasecampMemberStatus.ACTIVE, null),
                        tuple(pending, null, BasecampApplicationStatus.PENDING),
                        tuple(kicked, BasecampMemberStatus.KICKED, BasecampApplicationStatus.APPROVED));
    }

    @Test
    @DisplayName("[F-12][BC-06] 회원이 ACTIVE 멤버로 들어 있는 확정된 베이스캠프의 일정만 읽는다")
    void confirmedSchedulesOfActiveMembership() {
        // given
        Member leader = saveMember();
        Member viewer = saveMember();
        long spotId = insertSpot("장소", new Coordinate(37.5, 127.5));
        long confirmedMember = insertBasecamp(leader, spotId, "CONFIRMED", DAY, 4);
        insertMemberRow(confirmedMember, viewer, "MEMBER", "ACTIVE");
        long confirmedLeft = insertBasecamp(leader, spotId, "CONFIRMED", DAY, 4);
        insertMemberRow(confirmedLeft, viewer, "MEMBER", "LEFT");
        long recruitingMember = insertBasecamp(leader, spotId, "RECRUITING", DAY, 4);
        insertMemberRow(recruitingMember, viewer, "MEMBER", "ACTIVE");
        insertBasecamp(leader, spotId, "CONFIRMED", DAY, 4);

        // when
        List<ConfirmedScheduleRow> rows = mapper.selectConfirmedSchedules(viewer.getId());

        // then
        assertThat(rows).containsExactly(new ConfirmedScheduleRow(confirmedMember, DAY, DAY.plusDays(2)));
    }

    @Test
    @DisplayName("[F-12] 장소 요약은 장소의 이름과 유형을 읽고, 없는 장소는 null이다")
    void spotSummary() {
        // given
        long spotId = insertSpot("능선 끝 평지", new Coordinate(37.5, 127.5));

        // when & then
        assertThat(mapper.selectSpotSummary(spotId)).isEqualTo(new SpotSummaryRow(spotId, "능선 끝 평지", "BAKJI"));
        assertThat(mapper.selectSpotSummary(spotId + 1000)).isNull();
    }

    private static BasecampSearchCondition areaCondition(BasecampSearchCondition.Area area) {
        return new BasecampSearchCondition(area, null, null, null, false);
    }

    private static BasecampSearchCondition radiusCondition(Coordinate center, double radiusKm) {
        SpotSearchBox box = SpotSearchBox.around(center.lat(), center.lng(), radiusKm);
        BasecampSearchCondition.Nearby nearby = new BasecampSearchCondition.Nearby(
                center.lat(), center.lng(), radiusKm * 1000, box.swLat(), box.swLng(), box.neLat(), box.neLng());
        return new BasecampSearchCondition(null, nearby, null, null, false);
    }

    private Member saveMember() {
        return memberRepository.saveAndFlush(aMember().build());
    }

    private long insertSpot(String name, Coordinate at) {
        jdbc.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_GeomFromText(?, 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                name,
                at.wkt());
        return jdbc.queryForObject("SELECT MAX(id) FROM spot", Long.class);
    }

    // 캠프 리더의 멤버 행도 함께 넣어서 현재 인원이 1명으로 시작한다. 종료일은 출발일 이틀 뒤다.
    private long insertBasecamp(Member leader, long spotId, String status, LocalDate startDate, int capacity) {
        jdbc.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity, status,"
                        + " created_at, updated_at) VALUES (?, ?, '기존', '기존', ?, ?, ?, ?,"
                        + " '2026-10-05 03:00:00', '2026-10-05 03:00:00')",
                leader.getId(),
                spotId,
                startDate,
                startDate.plusDays(2),
                capacity,
                status);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
        insertMemberRow(id, leader, "LEADER", "ACTIVE");
        return id;
    }

    private void insertMemberRow(long basecampId, Member member, String role, String status) {
        jdbc.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, '2026-10-05 03:00:00', '2026-10-05 03:00:00', '2026-10-05 03:00:00')",
                basecampId,
                member.getId(),
                role,
                status);
    }

    private void insertApplicationRow(long basecampId, Member applicant, String status) {
        jdbc.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, created_at, updated_at)"
                        + " VALUES (?, ?, ?, '2026-10-05 03:00:00', '2026-10-05 03:00:00')",
                basecampId,
                applicant.getId(),
                status);
    }

    private record Coordinate(double lat, double lng) {

        // SRID 4326 좌표의 WKT는 위도, 경도 순서다.
        String wkt() {
            return String.format(Locale.ROOT, "POINT(%s %s)", lat, lng);
        }
    }
}
