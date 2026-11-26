package com.gm.demo.service;

import com.gm.demo.entity.response.AskResponse;
import com.gm.demo.rag.KnowledgeBaseLoader;
import com.gm.demo.tool.GmOpsWriteTool;
import com.gm.demo.tool.PlayerQueryTool;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 一句进、一句出；只读取数 + 写操作通过工具，operator 经 ToolContext 透传。
 * 会话记忆由 MessageChatMemoryAdvisor 自动管理；RAG 检索由 QuestionAnswerAdvisor 自动拼入 FAQ 原文。
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final String SYSTEM = """
            你是游戏运营管理后台的助手。
            只回答与游戏运营相关的问题；不确定就明确说不确定，不要编造数据。
            问题涉及某个具体玩家的等级、货币、状态或登录情况时，必须先调用 findPlayerByUsername 取数再回答。
            封禁、解封等写操作只在运营明确要求时执行，执行前必须在回答里复述目标玩家与后果。
            用中文回答，控制在 150 字以内。
            """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final String model;
    private final PlayerQueryTool playerQueryTool;
    private final GmOpsWriteTool gmOpsWriteTool;
    private final KnowledgeBaseLoader knowledgeBase;

    /**
     * 注入 Builder + ChatMemory + VectorStore + KnowledgeBaseLoader。
     * MessageChatMemoryAdvisor 自动管理会话记忆；
     * QuestionAnswerAdvisor 自动检索 FAQ 拼入 prompt，模型回答时能引用知识库原文。
     */
    @Autowired
    public AssistantService(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            VectorStore vectorStore,
            KnowledgeBaseLoader knowledgeBase,
            @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}") String model,
            PlayerQueryTool playerQueryTool,
            GmOpsWriteTool gmOpsWriteTool) {
        this.chatMemory = chatMemory;
        this.knowledgeBase = knowledgeBase;
        this.chatClient = builder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore)
                                .searchRequest(SearchRequest.builder().topK(3).build())
                                .build())
                .build();
        this.model = model;
        this.playerQueryTool = playerQueryTool;
        this.gmOpsWriteTool = gmOpsWriteTool;
    }

    /** 应用启动时自动灌入 FAQ 知识库。无 DASHSCOPE_API_KEY 时优雅失败（不阻止应用启动）。 */
    @PostConstruct
    void initKnowledge() {
        try {
            knowledgeBase.reindex();
        } catch (Exception e) {
            log.warn("[RAG] 知识库初始化失败（可能缺少 DASHSCOPE_API_KEY），RAG 问答暂不可用：{}", e.getMessage());
        }
    }

    /**
     * 问一句。conversationId 决定这条问答归入哪个会话的记忆窗口：
     * 传相同值 = 模型能看到之前几轮；不传则服务端每次生成新 UUID = 无记忆单轮。
     */
    public AskResponse ask(String question, String operator, String requestId, String conversationId) {
        long start = System.nanoTime();
        String rid = (requestId == null || requestId.isBlank())
                ? UUID.randomUUID().toString() : requestId;
        String cid = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;

        String answer = chatClient.prompt().user(question)
                .tools(playerQueryTool, gmOpsWriteTool)
                .toolContext(Map.of(
                        "operator", operator == null ? "unknown" : operator,
                        "requestId", rid))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .call().content();

        long tookMs = (System.nanoTime() - start) / 1_000_000;
        return new AskResponse(question, answer, model, tookMs);
    }

    /** 清除指定会话的历史记忆（“开新对话”按钮的后端支撑）。 */
    public void clearConversation(String conversationId) {
        chatMemory.clear(conversationId);
    }

    /** 重建 FAQ 知识库索引，返回灌入的 chunk 数。 */
    public int reindexKnowledge() {
        return knowledgeBase.reindex();
    }
}
