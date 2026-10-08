package com.pitchmap.common.audit;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 조치를 {@code admin_audit_log}에 기록한다.
 *
 * <p>호출하는 쪽의 {@code application} 서비스는 조치와 같은 트랜잭션 안에서 이 메서드를 불러야 한다.
 * 그래야 조치가 롤백될 때 기록도 함께 사라지고, 기록이 있는데 조치는 없는 상황이 생기지 않는다.
 * trust와 publicdata가 admin 모듈에 의존할 수 없어서 이 클래스는 {@code common}에 두었다. 트랜잭션을 직접 열지 않고,
 * 열려 있지 않으면 예외로 알린다.
 */
@Component
@RequiredArgsConstructor
public class AdminAuditRecorder {

    private final AdminAuditLogJpaRepository adminAuditLogRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * 호출하면 관리자 adminId가 targetType인 대상 targetId에 action을 한 기록을 저장한다. detail은 JSON으로 바꿔 저장하고, null이면 비워 둔다.
     * 신고 내용, 메모, 제재 사유 원문은 detail에 넣지 않는다.
     */
    public void record(long adminId, AdminAuditAction action, String targetType, long targetId, Object detail) {
        requireActiveTransaction();
        String detailJson = detail == null ? null : toJson(detail);
        adminAuditLogRepository.save(
                AdminAuditLog.record(adminId, action, targetType, targetId, detailJson, Instant.now(clock)));
    }

    // 트랜잭션 밖에서 저장하면 기록만 따로 커밋된다. 그러면 조치가 실패해도 하지 않은 조치의 기록이 남는다.
    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("감사 로그는 관리자 조치와 같은 트랜잭션 안에서 기록해야 합니다.");
        }
    }

    // detail에는 개인정보가 들어갈 수 있으므로 예외 메시지에 값을 싣지 않는다.
    private String toJson(Object detail) {
        try {
            return jsonMapper.writeValueAsString(detail);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "감사 로그 detail을 JSON으로 바꿀 수 없습니다. detail 타입="
                            + detail.getClass().getName(),
                    e);
        }
    }
}
