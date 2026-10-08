package com.pitchmap.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 관리자 조치 한 건의 기록이다. 한 번 기록하면 바꾸지 않으므로 변경 메서드가 없다.
 * 조치 대상은 테이블이 action마다 달라서 외래 키 없이 종류(targetType)와 ID로만 가리킨다.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private long adminId;

    @Enumerated(EnumType.STRING)
    private AdminAuditAction action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private long targetId;

    // 문자열을 그대로 JSON 컬럼에 넣는다. Hibernate는 String 속성의 값을 다시 인용부호로 감싸지 않는다.
    @JdbcTypeCode(SqlTypes.JSON)
    private String detail;

    @Column(name = "created_at")
    private Instant createdAt;

    private AdminAuditLog(
            long adminId, AdminAuditAction action, String targetType, long targetId, String detail, Instant now) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.createdAt = now;
    }

    /**
     * 호출하면 관리자 adminId가 now에 targetType인 대상 targetId에 action을 한 기록을 만든다. detailJson은 JSON 문자열이고 없으면 null이다.
     * action, 대상 종류, 시각이 비어 있으면 {@link IllegalArgumentException}을 던진다.
     */
    public static AdminAuditLog record(
            long adminId, AdminAuditAction action, String targetType, long targetId, String detailJson, Instant now) {
        if (action == null || now == null) {
            throw new IllegalArgumentException("감사 로그의 조치 종류나 기록 시각이 null입니다.");
        }
        if (targetType == null || targetType.isBlank()) {
            throw new IllegalArgumentException("감사 로그의 대상 종류가 비어 있습니다.");
        }
        return new AdminAuditLog(adminId, action, targetType, targetId, detailJson, now);
    }
}
