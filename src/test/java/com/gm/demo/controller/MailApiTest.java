package com.gm.demo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm.demo.repository.MailRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 邮件接口的集成测试：草稿 → 发送 → 重复发送 409 → 列表。 */
@SpringBootTest
@AutoConfigureMockMvc
class MailApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private MailRepository mailRepo;

    private final String subject = "test_mail_" + Long.toHexString(System.nanoTime());

    @AfterEach
    void cleanup() {
        mailRepo.findBySubject(subject).ifPresent(m -> mailRepo.deleteAllById(List.of(m.getId())));
    }

    private long createDraft() throws Exception {
        String body = om.writeValueAsString(java.util.Map.of(
                "subject", subject,
                "content", "测试邮件内容",
                "gold", 500,
                "diamond", 10));
        String resp = mvc.perform(post("/api/mails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", "tester")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp).get("id").asLong();
    }

    @Test
    void createDraftReturns201AndStatusIsDraft() throws Exception {
        createDraft();
    }

    @Test
    void sendTransitionsDraftToSent() throws Exception {
        long id = createDraft();
        mvc.perform(post("/api/mails/" + id + "/send").header("X-Operator", "tester"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty());
    }

    @Test
    void resendReturns409() throws Exception {
        long id = createDraft();
        mvc.perform(post("/api/mails/" + id + "/send").header("X-Operator", "tester"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/mails/" + id + "/send").header("X-Operator", "tester"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("不可重复发送")));
    }

    @Test
    void blankSubjectIsRejectedWith400() throws Exception {
        String body = om.writeValueAsString(java.util.Map.of("subject", "", "content", "x"));
        mvc.perform(post("/api/mails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
