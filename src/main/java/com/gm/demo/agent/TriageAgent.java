package com.gm.demo.agent;

import com.gm.demo.entity.domain.TicketCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 分诊 Agent：把自然语言工单描述映射到 TicketCategory 枚举 + 优先级。
 *
 * <p>职责严格限定在「自然语言 → 结构化意图」这一次文本转换上，
 * 不执行任何写操作、不检索知识库、不维护会话记忆。
 *
 * <p>从 ChatClient.Builder（prototype scope）构建独立实例，
 * 与 AssistantService 的 gmChatClient 完全隔离——无工具、无记忆、无 RAG，
 * 因此本 Agent 不可能调到 banPlayer/adjustCurrency，权限天然隔离。
 */
@Component
public class TriageAgent {

    private static final Logger log = LoggerFactory.getLogger(TriageAgent.class);

    private static final String SYSTEM = """
            你是游戏运营工单分诊员。根据工单内容，只输出一行，格式严格如下：
            CATEGORY=<枚举值>|PRIORITY=<high|medium|low>|REASON=<20字以内的分类依据>
            
            CATEGORY 可选值：BUG（功能异常/崩溃/显示错误）、CHEAT（作弊/外挂/利用漏洞）、\
            PAYMENT（充值/扣款/退款相关）、SUGGESTION（建议/优化需求）、OTHER（无法归类）。
            
            不要输出任何其他文字、解释或换行。
            """;

    private final ChatClient chatClient;

    @Autowired
    public TriageAgent(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(SYSTEM)
                .build();
    }

    /**
     * 分诊结果。
     * @param category 工单分类（一定合法，解析失败兜底 OTHER）
     * @param priority high/medium/low
     * @param reason   模型给出的分类依据
     */
    public record TriageResult(TicketCategory category, String priority, String reason) {}

    /** 对工单标题+描述进行分诊。 */
    public TriageResult triage(String title, String description) {
        String input = "标题：" + title + "\n内容：" + description;
        log.info("[分诊Agent] 输入 title={}", title);

        String raw = chatClient.prompt().user(input).call().content();
        log.info("[分诊Agent] 模型输出: {}", raw);

        return parse(raw);
    }

    /** 解析模型输出，任何解析失败都兜底为 OTHER/medium 而不是抛异常。 */
    private TriageResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new TriageResult(TicketCategory.OTHER, "medium", "模型未返回有效输出");
        }
        try {
            // 只取第一行（防止模型多输出了解释）
            String line = raw.trim().split("\n")[0];
            String[] parts = line.split("\\|");
            TicketCategory cat = TicketCategory.OTHER;
            String priority = "medium";
            String reason = "";

            for (String part : parts) {
                String[] kv = part.split("=", 2);
                if (kv.length < 2) continue;
                switch (kv[0].trim().toUpperCase()) {
                    case "CATEGORY" -> cat = parseCategory(kv[1].trim());
                    case "PRIORITY" -> priority = kv[1].trim().toLowerCase();
                    case "REASON" -> reason = kv[1].trim();
                }
            }
            return new TriageResult(cat, priority, reason);
        } catch (Exception e) {
            log.warn("[分诊Agent] 解析失败，兜底 OTHER: {}", e.getMessage());
            return new TriageResult(TicketCategory.OTHER, "medium", raw.trim());
        }
    }

    private static TicketCategory parseCategory(String value) {
        try {
            return TicketCategory.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TicketCategory.OTHER;
        }
    }
}
