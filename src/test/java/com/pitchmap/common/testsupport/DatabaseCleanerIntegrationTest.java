package com.pitchmap.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@IntegrationTest
class DatabaseCleanerIntegrationTest {

    private static final List<String> SEEDED_TABLES = List.of(
            "member",
            "email_verification",
            "login_history",
            "disposable_email_domain",
            "SPRING_SESSION",
            "SPRING_SESSION_ATTRIBUTES");

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("부모·자식 테이블과 대문자 테이블의 행을 모두 지우고 Flyway 이력은 남긴다")
    void cleanDeletesAllRowsExceptFlywayHistory() {
        // given
        seedRows();
        int historyRows = count("flyway_schema_history");

        // when
        new DatabaseCleaner(dataSource).clean();

        // then
        SEEDED_TABLES.forEach(table -> assertThat(count(table)).as(table).isZero());
        assertThat(historyRows).isPositive();
        assertThat(count("flyway_schema_history")).isEqualTo(historyRows);
    }

    @Test
    @DisplayName("정리한 뒤에는 FK 검사가 다시 켜져 있다")
    void foreignKeyChecksAreEnabledAfterClean() throws SQLException {
        // given: 풀 크기나 연결 재사용 여부와 무관하게 같은 물리 연결에서 정리하고 확인한다
        seedRows();

        try (Connection connection = dataSource.getConnection()) {
            // when
            new DatabaseCleaner(new SingleConnectionDataSource(connection, true)).clean();

            // then
            assertThat(foreignKeyChecks(connection)).isEqualTo(1);
        }
    }

    private int foreignKeyChecks(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT @@FOREIGN_KEY_CHECKS")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void seedRows() {
        jdbcTemplate.update("INSERT INTO member (id, email, nickname, status, role, created_at, updated_at) "
                + "VALUES (1, 'cleaner@example.com', 'cleaner', 'ACTIVE', 'USER', NOW(6), NOW(6))");
        jdbcTemplate.update("INSERT INTO email_verification "
                + "(member_id, code_hash, request_ip, expires_at, created_at, updated_at) "
                + "VALUES (1, REPEAT('a', 64), '127.0.0.1', NOW(6), NOW(6), NOW(6))");
        jdbcTemplate.update("INSERT INTO login_history (member_id, attempted_email, ip, success, created_at) "
                + "VALUES (1, 'cleaner@example.com', '127.0.0.1', TRUE, NOW(6))");
        jdbcTemplate.update("INSERT INTO disposable_email_domain (domain, source, created_at) "
                + "VALUES ('mailinator.example', 'ADMIN', NOW(6))");
        jdbcTemplate.update("INSERT INTO SPRING_SESSION "
                + "(PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME) "
                + "VALUES (REPEAT('p', 36), REPEAT('s', 36), 1, 1, 1800, 2)");
        jdbcTemplate.update(
                "INSERT INTO SPRING_SESSION_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES) "
                        + "VALUES (REPEAT('p', 36), 'attr', 'x')");
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
    }
}
