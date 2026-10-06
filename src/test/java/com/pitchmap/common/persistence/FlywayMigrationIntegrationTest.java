package com.pitchmap.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class FlywayMigrationIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Flyway flyway;

    @Test
    @DisplayName("[ADR-001] 빈 DB에 마이그레이션이 모두 적용되고 회원·세션 테이블이 만들어진다")
    void migrationsAreAppliedToEmptyDatabase() {
        // given: 컨텍스트가 뜰 때 Flyway가 빈 DB에 마이그레이션을 적용했다

        // when
        MigrationInfo[] applied = flyway.info().applied();
        MigrationInfo[] pending = flyway.info().pending();
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.tables"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'",
                String.class);

        // then
        assertThat(pending).isEmpty();
        assertThat(applied).isNotEmpty();
        assertThat(applied).allSatisfy(info -> assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS));
        assertThat(tableNames)
                .containsAll(List.of(
                        "member",
                        "email_verification",
                        "password_reset_token",
                        "login_history",
                        "disposable_email_domain",
                        "SPRING_SESSION",
                        "SPRING_SESSION_ATTRIBUTES"));
    }

    @Test
    @DisplayName("[EV-06] 이메일은 대소문자만 달라도 중복으로 거부한다")
    void emailUniquenessIgnoresCase() {
        // given
        insertMember("Alice@Example.com", "alice1");

        // when & then
        assertThatThrownBy(() -> insertMember("alice@example.com", "alice2")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("[EV-05] 탈퇴 회원처럼 이메일이 NULL인 회원 여러 명을 둘 수 있다")
    void multipleMembersWithNullEmailAreAllowed() {
        // given
        insertMember(null, "withdrawn1");

        // when
        insertMember(null, "withdrawn2");

        // then
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member WHERE email IS NULL", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("닉네임은 중복될 수 없다")
    void nicknameMustBeUnique() {
        // given
        insertMember("a@example.com", "same-nick");

        // when & then
        assertThatThrownBy(() -> insertMember("b@example.com", "same-nick")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("[PW-02] 같은 재설정 토큰 해시는 두 번 저장할 수 없다")
    void passwordResetTokenHashMustBeUnique() {
        // given
        long memberId = insertMember("a@example.com", "nick-a");
        String tokenHash = "a".repeat(64);
        insertPasswordResetToken(memberId, tokenHash);

        // when & then
        assertThatThrownBy(() -> insertPasswordResetToken(memberId, tokenHash))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("[EV-02] 인증 코드 시도 횟수는 0에서 시작한다")
    void attemptCountStartsAtZero() {
        // given
        long memberId = insertMember("a@example.com", "nick-a");

        // when
        insertEmailVerification(memberId);

        // then
        Integer attemptCount = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM email_verification WHERE member_id = ?", Integer.class, memberId);
        assertThat(attemptCount).isZero();
    }

    @Test
    @DisplayName("[EV-05] 회원 행을 지우면 인증 코드와 재설정 토큰은 함께 지워지고 로그인 기록은 남기며 member_id만 비워진다")
    void deletingMemberCascadesToTokensAndKeepsLoginHistory() {
        // given
        long memberId = insertMember("a@example.com", "nick-a");
        insertEmailVerification(memberId);
        insertPasswordResetToken(memberId, "b".repeat(64));
        jdbcTemplate.update(
                "INSERT INTO login_history (member_id, attempted_email, ip, success, created_at)"
                        + " VALUES (?, ?, ?, ?, NOW(6))",
                memberId,
                "a@example.com",
                "127.0.0.1",
                true);

        // when
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);

        // then
        assertThat(count("email_verification")).isZero();
        assertThat(count("password_reset_token")).isZero();
        assertThat(count("login_history")).isEqualTo(1);
        Long remainingMemberId = jdbcTemplate.queryForObject("SELECT member_id FROM login_history", Long.class);
        assertThat(remainingMemberId).isNull();
    }

    private long insertMember(String email, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO member (email, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, 'UNVERIFIED', 'USER', NOW(6), NOW(6))",
                email,
                nickname);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM member WHERE nickname = ?", Long.class, nickname);
        return id;
    }

    private void insertEmailVerification(long memberId) {
        jdbcTemplate.update(
                "INSERT INTO email_verification (member_id, code_hash, request_ip, expires_at, created_at, updated_at)"
                        + " VALUES (?, ?, '127.0.0.1', NOW(6), NOW(6), NOW(6))",
                memberId,
                "c".repeat(64));
    }

    private void insertPasswordResetToken(long memberId, String tokenHash) {
        jdbcTemplate.update(
                "INSERT INTO password_reset_token (member_id, token_hash, expires_at, created_at, updated_at)"
                        + " VALUES (?, ?, NOW(6), NOW(6), NOW(6))",
                memberId,
                tokenHash);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count;
    }
}
