package com.gm.demo.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm.demo.repository.MailRepository;
import com.gm.demo.repository.OperationLogRepository;
import com.gm.demo.repository.PlayerRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 审计日志集成测试：验证写操作确实留下审计记录，且可按操作人过滤。 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private PlayerRepository playerRepo;
    @Autowired
    private MailRepository mailRepo;
    @Autowired
    private OperationLogRepository auditRepo;

    private final String operator = "aud_" + Long.toHexString(System.nanoTime());
    private final String probeName = "aud_" + Long.toHexString(System.nanoTime());

    @AfterEach
    void cleanup() {
        auditRepo.deleteByOperator(operator);
        playerRepo.findByUsername(probeName).ifPresent(playerRepo::delete);
        mailRepo.findBySubject(probeName).ifPresent(mailRepo::delete);
    }

    @Test
    void banPlayerIsAudited() throws Exception {
        // 创建玩家
        String body = om.writeValueAsString(Map.of(
                "username", probeName,
                "nickname", "审计探针",
                "gold", 0,
                "diamond", 0));
        String resp = mvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", operator)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long playerId = om.readTree(resp).get("id").asLong();

        // 封禁
        mvc.perform(patch("/api/players/" + playerId + "/ban")
                        .header("X-Operator", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BANNED"));

        // 查审计
        mvc.perform(get("/api/audit-logs")
                        .param("operator", operator)
                        .param("action", "BAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].resource").value("Player"))
                .andExpect(jsonPath("$.content[0].detail", org.hamcrest.Matchers.containsString(probeName)));
    }

    @Test
    void sendMailIsAudited() throws Exception {
        // 创建草稿
        String body = om.writeValueAsString(Map.of(
                "subject", probeName,
                "content", "审计测试邮件"));
        String resp = mvc.perform(post("/api/mails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", operator)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long mailId = om.readTree(resp).get("id").asLong();

        // 发送
        mvc.perform(post("/api/mails/" + mailId + "/send")
                        .header("X-Operator", operator))
                .andExpect(status().isOk());

        // 查审计
        mvc.perform(get("/api/audit-logs")
                        .param("operator", operator)
                        .param("action", "SEND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].resource").value("Mail"));
    }

    @Test
    void emptyResultForUnknownOperator() throws Exception {
        mvc.perform(get("/api/audit-logs")
                        .param("operator", "nobody_" + System.nanoTime()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
