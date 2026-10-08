package com.pitchmap.admin.application;

import com.pitchmap.admin.infra.AdminAuditLogMapper;
import com.pitchmap.admin.infra.AdminAuditLogRow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 관리자가 감사 로그를 읽는다. */
@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AdminAuditLogMapper adminAuditLogMapper;
    private final JsonMapper jsonMapper;

    /**
     * 호출하면 감사 로그를 최근 기록부터 한 페이지 돌려준다. adminId, targetType, from, to가 null이 아니면 그 조건으로 거른다.
     * 기간은 from 이상 to 미만이다.
     *
     * <p>서비스는 다음 페이지가 있는지 알려고 한 행을 더 읽고, 그 행은 결과에서 뺀다. 그래서 전체 개수를 세는 쿼리를 따로 보내지 않는다.
     */
    @Transactional(readOnly = true)
    public AuditLogPage findLogs(Long adminId, String targetType, Instant from, Instant to, int page, int size) {
        long offset = (long) page * size;
        List<AdminAuditLogRow> rows = adminAuditLogMapper.selectLogs(adminId, targetType, from, to, offset, size + 1);
        boolean hasNext = rows.size() > size;
        List<AuditLogView> content = rows.stream().limit(size).map(this::toView).toList();
        return new AuditLogPage(content, page, size, hasNext);
    }

    private AuditLogView toView(AdminAuditLogRow row) {
        JsonNode detail = row.detail() == null ? null : jsonMapper.readTree(row.detail());
        return new AuditLogView(
                row.auditLogId(),
                row.adminId(),
                row.action(),
                row.targetType(),
                row.targetId(),
                detail,
                row.createdAt());
    }
}
