package com.gm.demo.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm.demo.entity.request.CreatePlayerRequest;
import com.gm.demo.repository.PlayerRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 玩家接口测试：Web 层 → Service → Repository → MySQL 全链路。
 * 断言重点放在状态码上（404/409/400），确保失败不会被降级成 200。
 *
 * <p>不加 @Transactional：回滚型测试测不到跨请求的唯一约束行为，而 409 那条依赖上一条真的落了库。
 *
 * <p>连的是共享的远端库，不再有一启动就清空的前提，所以用户名随机、跑完删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlayerApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    PlayerRepository repository;

    private final List<String> createdUsernames = new ArrayList<>();

    /** 随机用户名，避免与历史数据或并行运行相撞；长度与字符集都满足 DTO 校验。 */
    private String uniq(String tag) {
        String name = "t_" + tag + "_" + UUID.randomUUID().toString().substring(0, 8);
        createdUsernames.add(name);
        return name;
    }

    @AfterEach
    void cleanUpCreatedRows() {
        createdUsernames.forEach(u -> repository.findByUsername(u).ifPresent(repository::delete));
        createdUsernames.clear();
    }

    private String body(String username, String nickname, long gold, long diamond, String recharge)
            throws Exception {
        return json.writeValueAsString(new CreatePlayerRequest(
                username, nickname, gold, diamond, new BigDecimal(recharge)));
    }

    @Test
    @DisplayName("未注册的路径必须是 404，不能被兜底成 500")
    void unknownPathIs404Not500() throws Exception {
        mvc.perform(get("/api/players/"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)));
    }

    @Test
    @DisplayName("ping 探针返回 pong 与服务器时间")
    void pingWorks() throws Exception {
        mvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pong", equalTo(true)))
                .andExpect(jsonPath("$.serverTime", notNullValue()));
    }

    @Test
    @DisplayName("创建返回 201 + Location，详情能查到同一条")
    void createThenRead() throws Exception {
        String name = uniq("create");
        String location = mvc.perform(post("/api/players")
                        .header("X-Operator", "qa-alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, "接口玩家一", 500L, 10L, "30.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username", equalTo(name)))
                .andExpect(jsonPath("$.status", equalTo("NORMAL")))
                .andExpect(jsonPath("$.statusLabel", equalTo("正常")))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", equalTo(name)));
    }

    @Test
    @DisplayName("查不存在的 id 返回 404 + 统一错误体")
    void unknownIdIs404() throws Exception {
        mvc.perform(get("/api/players/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.message", equalTo("Player not found: id=999999")))
                .andExpect(jsonPath("$.path", equalTo("/api/players/999999")))
                .andExpect(jsonPath("$.traceId", notNullValue()));
    }

    @Test
    @DisplayName("重复用户名返回 409")
    void duplicateUsernameIs409() throws Exception {
        String payload = body(uniq("dup"), "重复测试", 0L, 0L, "0.00");
        mvc.perform(post("/api/players").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/players").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("@Valid 生效：短用户名 + 负金币返回 400 且逐字段报错")
    void validationFailureIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ab", "", -5L, 0L, "0.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("分页返回自定义壳，不含 Spring Data 内部字段")
    void listUsesOwnPageEnvelope() throws Exception {
        mvc.perform(get("/api/players").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.page", equalTo(0)))
                .andExpect(jsonPath("$.size", equalTo(2)))
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.paged").doesNotExist());
    }
}
