package com.gm.demo.controller;

import com.gm.demo.entity.request.CreatePlayerRequest;
import com.gm.demo.entity.response.PageResponse;
import com.gm.demo.entity.response.PlayerResponse;
import com.gm.demo.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 玩家管理接口。只做参数绑定与响应封装，业务规则一律在 Service。 */
@RestController
@RequestMapping("/api/players")
@Tag(name = "01-玩家管理", description = "查询玩家、创建玩家")
public class PlayerController {

    private final PlayerService service;

    @Autowired
    public PlayerController(PlayerService service) {
        this.service = service;
    }

    @Operation(summary = "分页搜索玩家", description = "kw 匹配用户名或昵称，留空表示不过滤")
    @GetMapping
    public PageResponse<PlayerResponse> list(
            @RequestParam(name = "kw", defaultValue = "") String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return PageResponse.from(service.search(keyword, page, size));
    }

    @Operation(summary = "查询玩家详情", description = "不存在返回 404 + 统一错误体")
    @GetMapping("/{id}")
    public PlayerResponse detail(@PathVariable Long id) {
        return service.getPlayer(id);
    }

    /**
     * 创建玩家，成功返回 201 + Location。
     *
     * <p>operator 属于"谁在调用"，因此走请求头而不是请求体；A9 接入登录后改由认证主体提供。
     */
    @Operation(summary = "创建玩家", description = "用户名重复返回 409，参数不合法返回 400")
    @PostMapping
    public ResponseEntity<PlayerResponse> create(
            @Valid @RequestBody CreatePlayerRequest req,
            @RequestHeader(name = "X-Operator", defaultValue = "unknown") String operator) {
        PlayerResponse created = service.createPlayer(req, operator);
        return ResponseEntity.created(URI.create("/api/players/" + created.id())).body(created);
    }

    @Operation(summary = "封禁玩家", description = "NORMAL → BANNED，已封禁返回 409")
    @PatchMapping("/{id}/ban")
    public PlayerResponse ban(
            @PathVariable Long id,
            @RequestHeader(name = "X-Operator", defaultValue = "unknown") String operator) {
        return service.ban(id, operator);
    }

    @Operation(summary = "解封玩家", description = "BANNED → NORMAL，非封禁态返回 409")
    @PatchMapping("/{id}/unban")
    public PlayerResponse unban(
            @PathVariable Long id,
            @RequestHeader(name = "X-Operator", defaultValue = "unknown") String operator) {
        return service.unban(id, operator);
    }
}
