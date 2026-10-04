package com.pitchmap.common.testsupport;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * 현재 스키마의 모든 테이블 행을 지운다. 테이블 목록은 information_schema에서 읽으므로 마이그레이션이 늘어도 고칠 곳이 없다.
 */
public final class DatabaseCleaner {

    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";
    private static final String FIND_TABLES = "SELECT table_name FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'";

    private final DataSource dataSource;

    public DatabaseCleaner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void clean() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            deleteAllRows(statement);
        } catch (SQLException e) {
            throw new IllegalStateException("테스트 DB 정리에 실패했다", e);
        }
    }

    private void deleteAllRows(Statement statement) throws SQLException {
        List<String> tables = findTables(statement);
        // 부모·자식 삭제 순서를 계산하지 않으려고 FK 검사를 끈다. 이 세션 변수는 풀의 물리 연결에 남으므로 반드시 되돌린다.
        statement.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : tables) {
                // TRUNCATE는 DDL이라 암묵적 커밋이 일어나고 테이블이 많으면 테스트마다 훨씬 느리다.
                statement.executeUpdate("DELETE FROM `" + table + "`");
            }
        } finally {
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private List<String> findTables(Statement statement) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery(FIND_TABLES)) {
            while (resultSet.next()) {
                addIfNotFlywayHistory(tables, resultSet.getString(1));
            }
        }
        return tables;
    }

    // Flyway 이력을 지우면 다음 컨텍스트가 마이그레이션을 다시 적용하려 해서 실패한다.
    private void addIfNotFlywayHistory(List<String> tables, String table) {
        if (FLYWAY_HISTORY_TABLE.equalsIgnoreCase(table)) {
            return;
        }
        tables.add(table);
    }
}
