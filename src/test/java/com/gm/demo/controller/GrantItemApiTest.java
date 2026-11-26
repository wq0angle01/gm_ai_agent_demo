package com.gm.demo.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm.demo.entity.domain.Player;
import com.gm.demo.repository.GameItemRepository;
import com.gm.demo.repository.PlayerItemRepository;
import com.gm.demo.repository.PlayerRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 道具发放集成测试：发放 → 堆叠合并 → 查背包 → 目录 → 404。 */
@SpringBootTest
@AutoConfigureMockMvc
class GrantItemApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private PlayerRepository playerRepo;
    @Autowired
    private GameItemRepository itemRepo;
    @Autowired
    private PlayerItemRepository playerItemRepo;

    private long playerId;
    private static final String ITEM_CODE = "POTION_HP";

    @BeforeEach
    void setup() {
        Player p = Player.create("item_test_" + Long.toHexString(System.nanoTime()), "道具测试员");
        playerId = playerRepo.save(p).getId();
    }

    @AfterEach
    void cleanup() {
        playerItemRepo.findByPlayerId(playerId).forEach(playerItemRepo::delete);
        playerRepo.findById(playerId).ifPresent(playerRepo::delete);
    }

    private void grant(int count) throws Exception {
        String body = om.writeValueAsString(Map.of(
                "playerId", playerId,
                "itemCode", ITEM_CODE,
                "count", count));
        mvc.perform(post("/api/items/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", "tester")
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void grantCreatesPlayerItem() throws Exception {
        grant(5);
        mvc.perform(get("/api/items/player/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemCode").value(ITEM_CODE))
                .andExpect(jsonPath("$[0].count").value(5));
    }

    /** 第二次发放应合并到同一条记录，count 累加。 */
    @Test
    void grantStacksExistingItem() throws Exception {
        grant(3);
        grant(4);
        mvc.perform(get("/api/items/player/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].count").value(7));
    }

    @Test
    void unknownItemCodeReturns404() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "playerId", playerId,
                "itemCode", "NONEXIST_ITEM",
                "count", 1));
        mvc.perform(post("/api/items/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", "tester")
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void catalogReturnsSeededItems() throws Exception {
        mvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(3)));
    }
}
