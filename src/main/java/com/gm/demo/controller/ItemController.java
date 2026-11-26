package com.gm.demo.controller;

import com.gm.demo.entity.response.GameItemResponse;
import com.gm.demo.entity.request.GrantItemRequest;
import com.gm.demo.entity.response.PlayerItemResponse;
import com.gm.demo.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 道具管理接口：发放、查背包、道具目录。 */
@RestController
@RequestMapping("/api/items")
@Tag(name = "06-道具管理")
public class ItemController {

    private final ItemService service;

    @Autowired
    public ItemController(ItemService service) {
        this.service = service;
    }

    @PostMapping("/grant")
    @Operation(summary = "发放道具给玩家（堆叠合并）")
    public PlayerItemResponse grant(
            @Valid @RequestBody GrantItemRequest req,
            @RequestHeader(value = "X-Operator", defaultValue = "system") String operator) {
        return service.grant(req.playerId(), req.itemCode(), req.count(), operator);
    }

    @GetMapping("/player/{playerId}")
    @Operation(summary = "查询玩家背包")
    public List<PlayerItemResponse> playerItems(@PathVariable Long playerId) {
        return service.listPlayerItems(playerId);
    }

    @GetMapping
    @Operation(summary = "道具目录")
    public List<GameItemResponse> catalog() {
        return service.listCatalog();
    }
}
