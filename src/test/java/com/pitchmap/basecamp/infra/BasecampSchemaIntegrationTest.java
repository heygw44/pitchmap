package com.pitchmap.basecamp.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class BasecampSchemaIntegrationTest {

    // SRID 4326 WKT는 위도, 경도 순서다. 서울시청 좌표다.
    private static final String SEOUL_CITY_HALL_WKT = "POINT(37.5665 126.978)";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("[ADR-001] 마이그레이션을 적용하면 베이스캠프·합류 신청·멤버 테이블이 모두 만들어진다")
    void basecampTablesExistAfterMigration() {
        // given: 컨텍스트가 뜰 때 Flyway가 빈 DB에 마이그레이션을 적용했다

        // when
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.tables"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'",
                String.class);

        // then
        assertThat(tableNames).containsAll(List.of("basecamp", "basecamp_application", "basecamp_member"));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 6})
    @DisplayName("[BC-02] 정원이 2명이나 6명인 베이스캠프는 저장된다")
    void basecampWithBoundaryCapacityIsSaved(int capacity) {
        // given
        long leaderId = insertMember("leader");
        long spotId = insertSpot("정원 경계 장소");

        // when
        insertBasecamp(leaderId, spotId, capacity);

        // then
        assertThat(count("basecamp")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7})
    @DisplayName("[BC-02] 정원이 2~6명 밖인 베이스캠프는 저장할 수 없다")
    void basecampWithCapacityOutOfRangeIsRejected(int capacity) {
        // given
        long leaderId = insertMember("leader");
        long spotId = insertSpot("정원 범위 밖 장소");

        // when & then
        assertThatThrownBy(() -> insertBasecamp(leaderId, spotId, capacity))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_basecamp_capacity");
        assertThat(count("basecamp")).isZero();
    }

    @Test
    @DisplayName("[BC-07] 같은 회원이 같은 베이스캠프에 합류 신청 행을 두 개 만들 수 없다")
    void memberCannotApplyToSameBasecampTwice() {
        // given
        long basecampId = insertBasecampWithLeader("leader");
        long applicantId = insertMember("applicant");
        insertApplication(basecampId, applicantId);

        // when & then
        assertThatThrownBy(() -> insertApplication(basecampId, applicantId)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("[BC-07] 같은 회원이 다른 베이스캠프에는 합류를 신청할 수 있다")
    void memberCanApplyToDifferentBasecamps() {
        // given
        long firstBasecampId = insertBasecampWithLeader("first-leader");
        long secondBasecampId = insertBasecampWithLeader("second-leader");
        long applicantId = insertMember("applicant");
        insertApplication(firstBasecampId, applicantId);

        // when
        insertApplication(secondBasecampId, applicantId);

        // then
        assertThat(count("basecamp_application")).isEqualTo(2);
    }

    @Test
    @DisplayName("[BC-07][BC-22] 같은 회원이 같은 베이스캠프에 멤버 행을 두 개 만들 수 없다")
    void memberCannotJoinSameBasecampTwice() {
        // given
        long basecampId = insertBasecampWithLeader("leader");
        long memberId = insertMember("member");
        insertBasecampMember(basecampId, memberId);

        // when & then
        assertThatThrownBy(() -> insertBasecampMember(basecampId, memberId)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("[BC-05][BC-21] 동성만 조건과 임박 탈퇴 표시는 값을 주지 않으면 false로 저장된다")
    void sameGenderOnlyAndEarlyLeaveDefaultToFalse() {
        // given
        long basecampId = insertBasecampWithLeader("leader");
        long memberId = insertMember("member");

        // when
        insertBasecampMember(basecampId, memberId);

        // then
        Boolean sameGenderOnly = jdbcTemplate.queryForObject(
                "SELECT same_gender_only FROM basecamp WHERE id = ?", Boolean.class, basecampId);
        Boolean earlyLeave = jdbcTemplate.queryForObject(
                "SELECT early_leave FROM basecamp_member WHERE basecamp_id = ?", Boolean.class, basecampId);
        assertThat(sameGenderOnly).isFalse();
        assertThat(earlyLeave).isFalse();
    }

    @Test
    @DisplayName("[ADR-001] 없는 장소를 가리키는 베이스캠프는 저장할 수 없다")
    void basecampMustReferenceExistingSpot() {
        // given
        long leaderId = insertMember("leader");
        long missingSpotId = 999_999L;

        // when & then
        assertThatThrownBy(() -> insertBasecamp(leaderId, missingSpotId, 4))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("basecamp")).isZero();
    }

    private long insertBasecampWithLeader(String leaderNickname) {
        long leaderId = insertMember(leaderNickname);
        long spotId = insertSpot(leaderNickname + "의 장소");
        return insertBasecamp(leaderId, spotId, 4);
    }

    private long insertMember(String nickname) {
        jdbcTemplate.update(
                "INSERT INTO member (email, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'USER', NOW(6), NOW(6))",
                nickname + "@example.com",
                nickname);
        return jdbcTemplate.queryForObject("SELECT id FROM member WHERE nickname = ?", Long.class, nickname);
    }

    private long insertSpot(String name) {
        jdbcTemplate.update(
                "INSERT INTO spot (type, name, location, weather_nx, weather_ny, status, created_at, updated_at)"
                        + " VALUES ('BAKJI', ?, ST_GeomFromText(?, 4326), 60, 127, 'ACTIVE', NOW(6), NOW(6))",
                name,
                SEOUL_CITY_HALL_WKT);
        return jdbcTemplate.queryForObject("SELECT id FROM spot WHERE name = ?", Long.class, name);
    }

    private long insertBasecamp(long leaderId, long spotId, int capacity) {
        jdbcTemplate.update(
                "INSERT INTO basecamp (leader_id, spot_id, title, description, start_date, end_date, capacity,"
                        + " status, created_at, updated_at)"
                        + " VALUES (?, ?, '함께 가는 백패킹', '초보도 환영합니다', '2026-11-01', '2026-11-02', ?,"
                        + " 'RECRUITING', NOW(6), NOW(6))",
                leaderId,
                spotId,
                capacity);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM basecamp", Long.class);
    }

    private void insertApplication(long basecampId, long applicantId) {
        jdbcTemplate.update(
                "INSERT INTO basecamp_application (basecamp_id, applicant_id, status, created_at, updated_at)"
                        + " VALUES (?, ?, 'PENDING', NOW(6), NOW(6))",
                basecampId,
                applicantId);
    }

    private void insertBasecampMember(long basecampId, long memberId) {
        jdbcTemplate.update(
                "INSERT INTO basecamp_member (basecamp_id, member_id, role, status, joined_at, created_at, updated_at)"
                        + " VALUES (?, ?, 'MEMBER', 'ACTIVE', NOW(6), NOW(6), NOW(6))",
                basecampId,
                memberId);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count;
    }
}
