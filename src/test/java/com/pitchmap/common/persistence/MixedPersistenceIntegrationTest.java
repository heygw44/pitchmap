package com.pitchmap.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class MixedPersistenceIntegrationTest {

    private static final String SOURCE_ADMIN = "ADMIN";
    private static final String SOURCE_PUBLIC = "PUBLIC";
    private static final Instant CREATED_AT = Instant.parse("2026-10-04T01:02:03.456789Z");
    // KST로는 다음 날(10-05 00:30)로 넘어가는 UTC 시각
    private static final Instant UTC_LATE_NIGHT = Instant.parse("2026-10-04T15:30:45.123456Z");
    private static final String UTC_LATE_NIGHT_WALL_CLOCK = "2026-10-04 15:30:45.123456";

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DomainProbeRepository repository;

    @Autowired
    private DomainProbeMapper mapper;

    @Test
    @DisplayName("[ADR-009] 한 트랜잭션에서 JPA와 MyBatis로 쓴 내용은 커밋 뒤 함께 보인다")
    void jpaAndMyBatisWritesInOneTransactionAreVisibleAfterCommit() {
        // given
        String jpaDomain = "commit-jpa.example.com";
        String myBatisDomain = "commit-mybatis.example.com";

        // when
        transactionTemplate.executeWithoutResult(status -> {
            repository.save(new DomainProbe(jpaDomain, SOURCE_ADMIN, CREATED_AT));
            mapper.insert(myBatisDomain, SOURCE_PUBLIC, CREATED_AT);
        });

        // then
        assertThat(countByDomain(jpaDomain)).isEqualTo(1);
        assertThat(countByDomain(myBatisDomain)).isEqualTo(1);
    }

    @Test
    @DisplayName("[ADR-009] 한 트랜잭션에서 JPA와 MyBatis로 쓴 뒤 예외로 롤백하면 둘 다 취소된다")
    void jpaAndMyBatisWritesInOneTransactionAreBothRolledBackOnException() {
        // given
        String jpaDomain = "rollback-jpa.example.com";
        String myBatisDomain = "rollback-mybatis.example.com";

        // when
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    entityManager.persist(new DomainProbe(jpaDomain, SOURCE_ADMIN, CREATED_AT));
                    // flush 없이는 JPA INSERT가 DB에 나가지 않아 롤백을 시험할 수 없다
                    entityManager.flush();
                    mapper.insert(myBatisDomain, SOURCE_PUBLIC, CREATED_AT);
                    throw new IllegalStateException("롤백 유도");
                }))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(countByDomain(jpaDomain)).isZero();
        assertThat(countByDomain(myBatisDomain)).isZero();
    }

    // MyBatis는 JPA의 쓰기 지연 큐를 모른다. 이 테스트가 "MyBatis로 읽기 전에 flush한다"는 규칙의 이유를 코드로 남긴다.
    // 같은 쿼리를 두 번 실행한다. mybatis.configuration.local-cache-scope가 statement가 아니면 flush한 뒤에도 첫 결과(빈 값)가 재사용돼 실패한다.
    @Test
    @DisplayName("[ADR-009] JPA 변경은 flush하기 전에는 MyBatis 조회에 보이지 않고 flush하면 보인다")
    void jpaChangeIsInvisibleToMyBatisUntilFlush() {
        // given
        String domain = "flush.example.com";

        // when
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(new DomainProbe(domain, SOURCE_ADMIN, CREATED_AT));

            // then
            assertThat(mapper.findByDomain(domain)).isEmpty();

            entityManager.flush();

            assertThat(mapper.findByDomain(domain)).isPresent();
        });
    }

    @Test
    @DisplayName("조회 DTO(record)는 열 순서와 상관없이 이름으로 매핑한다")
    void viewRecordIsMappedByColumnNameRegardlessOfColumnOrder() {
        // given
        String domain = "mapping.example.com";
        mapper.insert(domain, SOURCE_PUBLIC, CREATED_AT);

        // when
        DomainProbeView view = mapper.findByDomain(domain).orElseThrow();

        // then
        assertThat(view.domain()).isEqualTo(domain);
        assertThat(view.source()).isEqualTo(SOURCE_PUBLIC);
        assertThat(view.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @DisplayName("시각은 JVM 시간대와 무관하게 UTC로 저장되고 JPA·MyBatis가 같은 값을 읽는다")
    void instantIsStoredAsUtcAndReadIdenticallyByJpaAndMyBatis() {
        // given: integrationTest가 JVM 시간대를 KST로 띄운다. 이 가드가 없으면 UTC JVM에서 테스트가 조용히 의미를 잃는다.
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Seoul"));
        String jpaDomain = "utc-jpa.example.com";
        String myBatisDomain = "utc-mybatis.example.com";

        // when
        transactionTemplate.executeWithoutResult(
                status -> repository.save(new DomainProbe(jpaDomain, SOURCE_ADMIN, UTC_LATE_NIGHT)));
        transactionTemplate.executeWithoutResult(status -> mapper.insert(myBatisDomain, SOURCE_PUBLIC, UTC_LATE_NIGHT));

        // then: 저장된 날 값이 UTC 벽시계다
        assertThat(storedWallClock(jpaDomain)).isEqualTo(UTC_LATE_NIGHT_WALL_CLOCK);
        assertThat(storedWallClock(myBatisDomain)).isEqualTo(UTC_LATE_NIGHT_WALL_CLOCK);

        // then: 서로 상대편이 쓴 행을 읽어도 원래 Instant가 나온다
        DomainProbeView readByMyBatis = mapper.findByDomain(jpaDomain).orElseThrow();
        DomainProbe readByJpa =
                transactionTemplate.execute(status -> entityManager.find(DomainProbe.class, myBatisDomain));
        assertThat(readByMyBatis.createdAt()).isEqualTo(UTC_LATE_NIGHT);
        assertThat(readByJpa).isNotNull();
        assertThat(readByJpa.getCreatedAt()).isEqualTo(UTC_LATE_NIGHT);
    }

    private int countByDomain(String domain) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM disposable_email_domain WHERE domain = ?", Integer.class, domain);
        return count == null ? 0 : count;
    }

    private String storedWallClock(String domain) {
        return jdbcTemplate.queryForObject(
                "SELECT CAST(created_at AS CHAR) FROM disposable_email_domain WHERE domain = ?", String.class, domain);
    }
}
