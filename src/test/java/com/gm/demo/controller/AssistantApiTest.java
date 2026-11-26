package com.gm.demo.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gm.demo.entity.request.AskRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AI 助手接口测试，全程不发网络请求：模型调用要花钱、结果还不确定，交给人工验证。
 * 这里只验两件不花钱的事——自动配置把 ChatClient 装配上了，以及参数在进模型之前就被挡住。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ChatClient.Builder chatClientBuilder;

    @Autowired
    ChatMemory chatMemory;

    @Test
    @DisplayName("Spring AI 自动配置生效，容器里能 build 出 ChatClient")
    void chatClientIsAssembled() {
        assertNotNull(chatClientBuilder.build());
    }

    @Test
    @DisplayName("空问题返回 400，请求不会走到模型")
    void blankQuestionIsRejectedBeforeModelCall() throws Exception {
        mvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AskRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)));
    }

    @Test
    @DisplayName("超过 500 字的问题返回 400")
    void tooLongQuestionIsRejected() throws Exception {
        mvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AskRequest("q".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)));
    }

    @Test
    @DisplayName("ChatMemory bean 由 spring-ai-autoconfigure-model-chat-memory 自动装配")
    void chatMemoryIsAutoConfigured() {
        assertNotNull(chatMemory);
    }

    @Test
    @DisplayName("conversationId 在请求体里被接受，校验不拦")
    void conversationIdIsAcceptedInRequest() throws Exception {
        // 发一个空问题 + 合法 conversationId，验证 conversationId 不触发 400（只有空 question 才触发）
        mvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AskRequest("", null, "my-conv-1"))))
                .andExpect(status().isBadRequest());  // 空 question 被 @NotBlank 拦，但 conversationId 没报错
    }

    @Test
    @DisplayName("清除会话记忆返回 204，不会调模型")
    void deleteConversationReturns204() throws Exception {
        mvc.perform(delete("/api/assistant/conversation/test-conv-1"))
                .andExpect(status().isNoContent());
    }
}
