package com.pitchmap.common.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

// 이 테스트는 확장이 테스트 사이에 하는 일(DB 행 삭제, 시계 복원)을 확인한다.
// 그래서 두 테스트가 순서대로 실행된다는 점에 일부러 기대고 있다.
@IntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTestIsolationIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock clock;

    @Test
    @Order(1)
    @DisplayName("상태를 남긴다: 회원과 차단 도메인 행을 넣고 시계를 하루 옮긴다")
    void leavesStateBehind() {
        jdbcTemplate.update(
                "INSERT INTO member (email, nickname, status, role, created_at, updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'USER', NOW(6), NOW(6))",
                TestSequence.email(),
                TestSequence.nickname());
        jdbcTemplate.update(
                "INSERT INTO disposable_email_domain (domain, source, created_at) VALUES (?, 'MANUAL', NOW(6))",
                TestSequence.unique("mailinator") + ".example");
        clock.advance(Duration.ofDays(1));

        assertThat(count("member")).isEqualTo(1);
        assertThat(count("disposable_email_domain")).isEqualTo(1);
        assertThat(clock.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT.plus(Duration.ofDays(1)));
    }

    @Test
    @Order(2)
    @DisplayName("앞선 테스트가 남긴 DB 행과 시계 이동은 다음 테스트가 시작될 때 사라져 있다")
    void previousTestStateIsGone() {
        assertThat(count("member")).isZero();
        assertThat(count("disposable_email_domain")).isZero();
        assertThat(clock.instant()).isEqualTo(MutableClock.DEFAULT_INSTANT);
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count;
    }
}
