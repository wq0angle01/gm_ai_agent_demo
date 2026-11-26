package com.gm.demo.controller;

import com.gm.demo.entity.request.AskRequest;
import com.gm.demo.entity.response.AskResponse;
import com.gm.demo.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 助手入口。校验通过后把文本交给 Service，本身不碰模型。 */
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "02-AI助手", description = "会话记忆 + 工具调用")
public class AssistantController {

    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @Operation(summary = "问一句",
            description = "多轮会话传 conversationId（相同值 = 模型能看到历史）；写操作需带 X-Operator")
    @PostMapping("/ask")
    public AskResponse ask(
            @Valid @RequestBody AskRequest req,
            @RequestHeader(value = "X-Operator", defaultValue = "unknown") String operator) {
        return service.ask(req.question(), operator, req.requestId(), req.conversationId());
    }

    @Operation(summary = "清除会话记忆",
            description = "“开新对话”按钮的后端：清除指定 conversationId 的历史消息，下次提问模型看不到之前的内容")
    @DeleteMapping("/conversation/{conversationId}")
    public ResponseEntity<Void> clearConversation(@PathVariable String conversationId) {
        service.clearConversation(conversationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "重建 FAQ 知识库索引",
            description = "重新扫描 classpath:faq/*.md 并灌入向量库。内存版每次启动自动重建，此端点用于修改 FAQ 后手动刷新")
    @PostMapping("/reindex")
    public java.util.Map<String, Integer> reindex() {
        int chunks = service.reindexKnowledge();
        return java.util.Map.of("indexedChunks", chunks);
    }
}
