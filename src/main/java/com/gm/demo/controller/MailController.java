package com.gm.demo.controller;

import com.gm.demo.entity.domain.MailStatus;
import com.gm.demo.entity.request.CreateMailRequest;
import com.gm.demo.entity.response.MailResponse;
import com.gm.demo.service.MailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 邮件管理接口：创建草稿、发送、查询。 */
@RestController
@RequestMapping("/api/mails")
@Tag(name = "03-邮件管理")
public class MailController {

    private final MailService service;

    @Autowired
    public MailController(MailService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "创建邮件草稿")
    public ResponseEntity<MailResponse> create(
            @Valid @RequestBody CreateMailRequest req,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        MailResponse resp = service.createDraft(req, operator);
        return ResponseEntity.created(URI.create("/api/mails/" + resp.id())).body(resp);
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "发送邮件（DRAFT → SENT）")
    public MailResponse send(
            @PathVariable Long id,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        return service.send(id, operator);
    }

    @GetMapping
    @Operation(summary = "邮件列表（分页，可按状态过滤）")
    public Page<MailResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return service.listByStatus(MailStatus.valueOf(status), page, size);
        }
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "邮件详情")
    public MailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
