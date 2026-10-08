package com.pitchmap.common.audit;

import static com.pitchmap.member.domain.MemberBuilder.aMember;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pitchmap.common.testsupport.IntegrationTest;
import com.pitchmap.member.infra.MemberJpaRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class AdminAuditRecorderIntegrationTest {

    @Autowired
    private AdminAuditRecorder recorder;

    @Autowired
    private AdminAuditLogJpaRepository repository;

    @Autowired
    private MemberJpaRepository memberRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    private long adminId;

    @BeforeEach
    void setUp() {
        adminId = memberRepository.saveAndFlush(aMember().build()).getId();
    }

    @Test
    @DisplayName("[F-21] 트랜잭션 안에서 기록하면 관리자, 조치, 대상, JSON detail, 기록 시각이 저장된다")
    void recordInTransactionSavesRow() {
        transactionTemplate.executeWithoutResult(status -> recorder.record(
                adminId,
                AdminAuditAction.SANCTION_LIFT,
                AdminAuditTargetType.SANCTION,
                7L,
                Map.of("fromStatus", "ACTIVE", "toStatus", "LIFTED")));

        List<AdminAuditLog> logs = repository.findAll();
        assertThat(logs).hasSize(1);
        AdminAuditLog log = logs.get(0);
        assertThat(log.getAdminId()).isEqualTo(adminId);
        assertThat(log.getAction()).isEqualTo(AdminAuditAction.SANCTION_LIFT);
        assertThat(log.getTargetType()).isEqualTo("SANCTION");
        assertThat(log.getTargetId()).isEqualTo(7L);
        assertThat(log.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(log.getDetail()).contains("\"fromStatus\": \"ACTIVE\"").contains("\"toStatus\": \"LIFTED\"");
    }

    @Test
    @DisplayName("[F-21] detail이 없으면 NULL로 저장한다")
    void recordWithoutDetail() {
        transactionTemplate.executeWithoutResult(status ->
                recorder.record(adminId, AdminAuditAction.SYNC_JOB_RUN, AdminAuditTargetType.SYNC_JOB_RUN, 1L, null));

        assertThat(repository.findAll())
                .singleElement()
                .satisfies(log -> assertThat(log.getDetail()).isNull());
    }

    @Test
    @DisplayName("[F-21] 트랜잭션 없이 기록하면 IllegalStateException을 던지고 아무것도 저장하지 않는다")
    void recordWithoutTransactionIsRejected() {
        assertThatThrownBy(() -> recorder.record(
                        adminId, AdminAuditAction.REPORT_ACTION, AdminAuditTargetType.MEMBER_REPORT, 1L, Map.of()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("[F-21] 조치와 같은 트랜잭션이 롤백되면 감사 로그도 남지 않는다")
    void rolledBackActionLeavesNoAuditLog() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    recorder.record(
                            adminId, AdminAuditAction.REPORT_ACTION, AdminAuditTargetType.MEMBER_REPORT, 1L, Map.of());
                    throw new IllegalStateException("조치 실패");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isZero();
    }
}
