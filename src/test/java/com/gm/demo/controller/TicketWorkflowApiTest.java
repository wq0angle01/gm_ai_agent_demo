package com.gm.demo.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm.demo.repository.OperationLogRepository;
import com.gm.demo.repository.TicketRepository;
import com.gm.demo.service.TicketWorkflowOrchestrator;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 多 Agent 工单编排的接口测试。
 *
 * <p><b>刻意不覆盖「真跑完整流程」</b>：那会发三次模型调用（分诊/草拟/审核），
 * 既产生费用又因输出不确定而无法稳定断言，交给人工验证。
 * 这里只验不花钱的三件事：编排器装配上了、工单不存在挡成 404、非 OPEN 工单短路不进模型。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketWorkflowApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private TicketRepository repo;
    @Autowired
    private OperationLogRepository auditRepo;
    @Autowired
    private TicketWorkflowOrchestrator orchestrator;

    private final String player = "wf_test_" + Long.toHexString(System.nanoTime());
    private final String operator = "wf_tester_" + Long.toHexString(System.nanoTime());

    @AfterEach
    void cleanup() {
        repo.findByPlayerUsername(player).forEach(repo::delete);
        auditRepo.deleteByOperator(operator);
        auditRepo.deleteByOperator("ai-workflow");
    }

    private long createTicket() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "title", "充值没到账_" + player,
                "description", "我昨天充值了648但钻石没有增加，订单号TXN_20261120_001",
                "playerUsername", player,
                "category", "PAYMENT"));
        String resp = mvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", operator)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("id").asLong();
    }

    @Test
    @DisplayName("编排器 bean 装配成功（三个 Agent + ChatClient.Builder 都注入到位）")
    void orchestratorIsAssembled() {
        assertNotNull(orchestrator);
    }

    @Test
    @DisplayName("工单不存在返回 404，不会走到模型")
    void missingTicketReturns404() throws Exception {
        mvc.perform(post("/api/tickets/999999999/auto-workflow"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("非 OPEN 状态工单短路返回，不进分诊流程")
    void nonOpenTicketShortCircuits() throws Exception {
        long id = createTicket();
        // 先流转到 ASSIGNED，此时再触发编排应短路
        mvc.perform(patch("/api/tickets/" + id + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", operator)
                        .content(om.writeValueAsString(Map.of("assignee", "cs_01"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tickets/" + id + "/auto-workflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(id))
                .andExpect(jsonPath("$.finalStatus").value("ASSIGNED"))
                // steps 只有一条“短路”，证明三个 Agent 都没被调用
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].agent").value("Orchestrator"));
    }
}
