package com.gm.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm.demo.repository.OperationLogRepository;
import com.gm.demo.repository.TicketRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 工单接口集成测试：状态机全流程 + 非法跳转 409 + 校验。 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private TicketRepository repo;
    @Autowired
    private OperationLogRepository auditRepo;

    private final String player = "tk_test_" + Long.toHexString(System.nanoTime());
    private final String operator = "tk_tester_" + Long.toHexString(System.nanoTime());

    @AfterEach
    void cleanup() {
        repo.findByPlayerUsername(player).forEach(repo::delete);
        auditRepo.deleteByOperator(operator);
    }

    private long createTicket() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "title", "测试工单_" + player,
                "description", "这是一条自动化测试工单",
                "playerUsername", player,
                "category", "BUG"));
        String resp = mvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", operator)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.category").value("BUG"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("id").asLong();
    }

    @Test
    void fullLifecycle() throws Exception {
        long id = createTicket();

        // OPEN → ASSIGNED
        mvc.perform(patch("/api/tickets/" + id + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", operator)
                        .content(om.writeValueAsString(Map.of("assignee", "cs_01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignee").value("cs_01"));

        // ASSIGNED → PROCESSING
        mvc.perform(patch("/api/tickets/" + id + "/process")
                        .header("X-Operator", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        // PROCESSING → RESOLVED
        mvc.perform(patch("/api/tickets/" + id + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", operator)
                        .content(om.writeValueAsString(Map.of("resolution", "已修复并补偿"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("已修复并补偿"));

        // RESOLVED → CLOSED
        mvc.perform(patch("/api/tickets/" + id + "/close")
                        .header("X-Operator", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
    }

    @Test
    void illegalTransitionReturns409() throws Exception {
        long id = createTicket();
        // OPEN 状态直接 close 应 409
        mvc.perform(patch("/api/tickets/" + id + "/close")
                        .header("X-Operator", operator))
                .andExpect(status().isConflict());
    }

    @Test
    void blankTitleRejectedWith400() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "title", "",
                "description", "x",
                "playerUsername", player));
        mvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", operator)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFilterByStatus() throws Exception {
        createTicket();
        mvc.perform(get("/api/tickets")
                        .param("status", "OPEN")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.playerUsername=='" + player + "')]").isNotEmpty());
    }
}
