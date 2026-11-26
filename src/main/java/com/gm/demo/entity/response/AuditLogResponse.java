package com.gm.demo.entity.response;

import com.gm.demo.entity.domain.OperationLog;
import java.time.Instant;

/** 审计日志响应视图。 */
public record AuditLogResponse(
        Long id,
        String action,
        String resource,
        String resourceId,
        String detail,
        String operator,
        boolean success,
        Instant createdAt) {

    public static AuditLogResponse from(OperationLog o) {
        return new AuditLogResponse(
                o.getId(), o.getAction(), o.getResource(), o.getResourceId(),
                o.getDetail(), o.getOperator(), o.isSuccess(), o.getCreatedAt());
    }
}
