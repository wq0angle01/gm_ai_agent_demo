package com.gm.demo.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 合规审核 Agent：检查处理方案是否违反运营红线。
 *
 * <p>职责同样是「自然语言 → 结构化判定」——只输出一行裁决，不修改方案、不执行动作。
 * 审核结果由编排器（Java 代码）决定是否继续流转。
 *
 * <p>无 RAG、无工具、无记忆：它的判断依据完全来自 system prompt 里硬编码的红线规则，
 * 这样保证审核标准确定、可审计，不受知识库变更影响。
 */
@Component
public class ComplianceAgent {

    private static final Logger log = LoggerFactory.getLogger(ComplianceAgent.class);

    private static final String SYSTEM = """
            你是游戏运营合规审核员。审核客服拟定的处理方案是否违反以下红线：
            1. 单次金币补偿不得超过 50000，钻石补偿不得超过 5000
            2. 不得同时对多名玩家执行封禁
            3. 退款金额不得超过玩家累计充值
            4. 不得在没有确认玩家身份的情况下发放补偿
            
            只输出一行裁决，格式严格如下（二选一）：
            PASS
            REJECT=<不超过40字的拒绝原因>
            
            不要输出其他任何文字。
            """;

    private final ChatClient chatClient;

    @Autowired
    public ComplianceAgent(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(SYSTEM)
                .build();
    }

    /**
     * 合规裁决结果。
     * @param approved 是否通过
     * @param note     拒绝原因（通过时为 null）
     */
    public record ComplianceVerdict(boolean approved, String note) {}

    /**
     * 审核处理方案。
     *
     * @param draft    客服 Agent 草拟的方案文本
     * @param category 工单分类（帮助审核员理解上下文）
     */
    public ComplianceVerdict review(String draft, String category) {
        String input = "工单分类：%s\n处理方案：%s".formatted(category, draft);
        log.info("[合规Agent] 审核方案 category={}", category);

        try {
            String raw = chatClient.prompt().user(input).call().content();
            log.info("[合规Agent] 裁决输出: {}", raw);
            return parse(raw);
        } catch (Exception e) {
            // 模型调用失败时保守处理：拒绝并标记人工复核
            log.warn("[合规Agent] 调用失败，保守拒绝: {}", e.getMessage());
            return new ComplianceVerdict(false, "审核系统异常，需人工复核");
        }
    }

    private ComplianceVerdict parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ComplianceVerdict(false, "模型未返回有效裁决");
        }
        String line = raw.trim().split("\n")[0].toUpperCase();
        if (line.startsWith("PASS")) {
            return new ComplianceVerdict(true, null);
        }
        if (line.startsWith("REJECT")) {
            String note = line.contains("=") ? line.substring(line.indexOf('=') + 1) : "方案不合规";
            return new ComplianceVerdict(false, note);
        }
        // 无法解析的格式，保守拒绝
        return new ComplianceVerdict(false, "裁决格式异常：" + raw.trim());
    }
}
