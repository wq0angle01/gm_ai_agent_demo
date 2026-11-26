package com.gm.demo.controller;

import com.gm.demo.entity.domain.NoticeStatus;
import com.gm.demo.entity.request.CreateNoticeRequest;
import com.gm.demo.entity.response.NoticeResponse;
import com.gm.demo.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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

/** 公告管理接口：创建草稿、发布、查询。 */
@RestController
@RequestMapping("/api/notices")
@Tag(name = "05-公告管理")
public class NoticeController {

    private final NoticeService service;

    @Autowired
    public NoticeController(NoticeService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "创建公告草稿")
    public ResponseEntity<NoticeResponse> create(
            @Valid @RequestBody CreateNoticeRequest req,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        NoticeResponse resp = service.createDraft(req, operator);
        return ResponseEntity.created(URI.create("/api/notices/" + resp.id())).body(resp);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布公告（DRAFT → PUBLISHED）")
    public NoticeResponse publish(
            @PathVariable Long id,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        return service.publish(id, operator);
    }

    @GetMapping
    @Operation(summary = "公告列表（分页，可按状态过滤）")
    public Page<NoticeResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        NoticeStatus st = (status != null && !status.isBlank())
                ? NoticeStatus.valueOf(status) : null;
        return service.list(st, page, size);
    }

    @GetMapping("/active")
    @Operation(summary = "当前生效公告（按置顶/优先级排序）")
    public List<NoticeResponse> active() {
        return service.findActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "公告详情")
    public NoticeResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
