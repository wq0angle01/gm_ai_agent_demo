package com.gm.demo.agent;

import com.gm.demo.entity.domain.TicketCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 客服处理 Agent：根据工单内容 + FAQ 知识库检索结果，草拟处理方案。
 *
 * <p>与 TriageAgent 的区别：挂了 QuestionAnswerAdvisor，每次调用自动从 VectorStore
 * 检索 topK 条最相关的 FAQ 片段拼入 prompt，让模型基于真实操作规程回答而非凭空编造。
 *
 * <p>同样从 ChatClient.Builder 构建独立实例——无写工具、无会话记忆，
 * 模型输出的只是一段文本方案，不执行任何副作用。
 */
@Component
public class SupportAgent {

    private static final Logger log = LoggerFactory.getLogger(SupportAgent.class);

    private static final String SYSTEM = """
            你是游戏客服专员。根据工单内容和知识库中的操作规程，拟一份处理方案。
            要求：
            1. 包含具体处理步骤（2-4条）
            2. 如涉及补偿，写明币种和数量
            3. 控制在 150 字以内
            4. 如果知识库信息不足以处理，明确写"需人工介入：缺少XXX信息"
            用中文输出，不要加标题或前缀。
            """;

    private final ChatClient chatClient;

    @Autowired
    public SupportAgent(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(3).build())
                                .build())
                .build();
    }

    /**
     * 根据工单信息草拟处理方案。
     *
     * @param title       工单标题
     * @param description 工单描述
     * @param category    分诊结果（已由 TriageAgent 确定）
     * @return 处理方案文本；调用失败返回兜底提示
     */
    public String draftResolution(String title, String description, TicketCategory category) {
        String input = "分类：%s\n标题：%s\n内容：%s".formatted(
                category.label(), title, description);
        log.info("[客服Agent] 草拟方案 category={} title={}", category, title);

        try {
            String draft = chatClient.prompt().user(input).call().content();
            log.info("[客服Agent] 方案输出长度={} 字", draft == null ? 0 : draft.length());
            return (draft == null || draft.isBlank())
                    ? "需人工介入：模型未返回有效处理方案" : draft.trim();
        } catch (Exception e) {
            // 上游模型错误或 RAG 检索失败时不阻断流程，标记需人工
            log.warn("[客服Agent] 调用失败: {}", e.getMessage());
            return "需人工介入：处理方案生成失败（" + e.getClass().getSimpleName() + "）";
        }
    }
}
