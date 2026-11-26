package com.gm.demo.controller;

import com.gm.demo.entity.response.AuditLogResponse;
import com.gm.demo.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 操作审计查询接口（只读，不提供写入——写入由各业务 Service 内部调用 AuditService）。 */
@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "04-操作审计")
public class AuditController {

    private final AuditService auditService;

    @Autowired
    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "审计日志分页查询（可按操作人/动作/资源过滤）")
    public Page<AuditLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resource) {
        return auditService.search(operator, action, resource, page, size);
    }
}
