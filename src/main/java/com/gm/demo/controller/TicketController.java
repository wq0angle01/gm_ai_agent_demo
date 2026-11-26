package com.gm.demo.controller;

import com.gm.demo.entity.domain.TicketCategory;
import com.gm.demo.entity.domain.TicketStatus;
import com.gm.demo.entity.request.CreateTicketRequest;
import com.gm.demo.entity.response.TicketResponse;
import com.gm.demo.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 工单管理接口：创建、状态流转（指派→处理→解决→关闭）、列表、详情。 */
@RestController
@RequestMapping("/api/tickets")
@Tag(name = "07-工单管理")
public class TicketController {

    private final TicketService service;

    @Autowired
    public TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "创建工单")
    public ResponseEntity<TicketResponse> create(
            @Valid @RequestBody CreateTicketRequest req,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        TicketResponse resp = service.create(req, operator);
        return ResponseEntity.created(URI.create("/api/tickets/" + resp.id())).body(resp);
    }

    @PatchMapping("/{id}/assign")
    @Operation(summary = "指派工单（OPEN → ASSIGNED）")
    public TicketResponse assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignRequest req,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        return service.assign(id, req.assignee(), operator);
    }

    @PatchMapping("/{id}/process")
    @Operation(summary = "开始处理（ASSIGNED → PROCESSING）")
    public TicketResponse process(
            @PathVariable Long id,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        return service.startProcessing(id, operator);
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "标记已解决（PROCESSING → RESOLVED）")
    public TicketResponse resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveRequest req,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        return service.resolve(id, req.resolution(), operator);
    }

    @PatchMapping("/{id}/close")
    @Operation(summary = "关闭工单（RESOLVED → CLOSED）")
    public TicketResponse close(
            @PathVariable Long id,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        return service.close(id, operator);
    }

    @GetMapping
    @Operation(summary = "工单列表（分页，可按状态/分类过滤）")
    public Page<TicketResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        TicketStatus st = (status != null && !status.isBlank()) ? TicketStatus.valueOf(status) : null;
        TicketCategory cat = (category != null && !category.isBlank()) ? TicketCategory.valueOf(category) : null;
        return service.list(st, cat, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "工单详情")
    public TicketResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/player/{username}")
    @Operation(summary = "按玩家查工单")
    public List<TicketResponse> byPlayer(@PathVariable String username) {
        return service.findByPlayer(username);
    }

    // ---------- 内部请求体 ----------

    /** 指派请求体。 */
    public record AssignRequest(@NotBlank @Size(max = 64) String assignee) {}

    /** 解决请求体。 */
    public record ResolveRequest(@NotBlank @Size(max = 1000) String resolution) {}
}
