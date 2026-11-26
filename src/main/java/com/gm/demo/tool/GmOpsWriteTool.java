package com.gm.demo.tool;

import com.gm.demo.entity.response.PlayerResponse;
import com.gm.demo.error.ConflictException;
import com.gm.demo.service.PlayerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 给模型使用的**写操作**工具集：封禁 / 解封玩家。
 *
 * <p>与 PlayerQueryTool 的只读不同，这里每一步都会改库并落审计，是「智能体区别于聊天机器人」的那一步。
 * 正因为能改数据，护栏比能力更重要，本类焊死了三条：
 *
 * <ol>
 *   <li><b>operator 不进模型入参</b>：执行者身份从 {@link ToolContext} 取（服务端在请求级塞进去的），
 *       而不是 @ToolParam。模型可见、可填的任何字段都不可信——把 operator 交给模型填，
 *       等于让一次 Prompt Injection 就能伪造"是某某运营授意的"，审计当场失去意义。
 *       框架（MethodToolCallback）会把 ToolContext 类型的参数从模型 schema 里剔除并自动注入。</li>
 *   <li><b>复用已审计的 Service 方法</b>：工具不自己写 SQL，只调 PlayerService.ban/unban——
 *       那里已有状态校验 + 审计落库，是同进程内 AI 与人工共用的最后一道闸门。</li>
 *   <li><b>返回一行人类可读文本</b>：改成功了说什么、失败了说什么，模型据此自我纠正，
 *       而不是把整个 PlayerResponse JSON 塞回报文推高 token。</li>
 * </ol>
 */
@Component
public class GmOpsWriteTool {

    private static final Logger log = LoggerFactory.getLogger(GmOpsWriteTool.class);
    private static final String OPERATOR_KEY = "operator";

    private final PlayerService playerService;
    private final IdempotencyGuard idempotency;

    @Autowired
    public GmOpsWriteTool(PlayerService playerService, IdempotencyGuard idempotency) {
        this.playerService = playerService;
        this.idempotency = idempotency;
    }

    @Tool(description = """
            封禁指定玩家（不可逆的写操作，会立即禁止其登录）。仅在运营已明确授意、\
            且已在对话中向操作人复述过目标玩家与后果后才可调用。登录名区分大小写，形如 hero_001。""")
    public String banPlayer(
            @ToolParam(description = "要封禁的玩家登录名，例如 hero_001") String username,
            ToolContext toolContext) {
        String operator = operatorOf(toolContext);
        // 分帧①：模型填的业务参数 + 服务端注入的可信 operator，两者来源不同，日志分开看
        log.info("[写工具] banPlayer 入参 username={}（模型填） operator={}（服务端 context 注入）",
                username, operator);

        PlayerResponse found = playerService.findByUsername(username).orElse(null);
        if (found == null) {
            // 错误语义翻译成模型能自我纠正的自然语言，而不是抛异常让链路中断
            return "未找到登录名为 " + username + " 的玩家，未执行任何封禁。请核对登录名，不要声称已封禁。";
        }
        try {
            playerService.ban(found.id(), operator);
            log.info("[写工具] banPlayer 成功 id={} username={} operator={}", found.id(), username, operator);
            return "已封禁玩家 " + username + "（id=" + found.id() + "），已记审计。";
        } catch (ConflictException e) {
            // 已经封禁 → 幂等，如实告诉模型"无需重复"，避免它再调一次
            log.info("[写工具] banPlayer 跳过（已封禁） username={} operator={}", username, operator);
            return "玩家 " + username + " 当前已是封禁状态，无需重复操作。";
        }
    }

    @Tool(description = """
            解除指定玩家的封禁（写操作，恢复其登录）。仅在运营已明确授意后才可调用。\
            登录名区分大小写，形如 hero_001。""")
    public String unbanPlayer(
            @ToolParam(description = "要解封的玩家登录名，例如 hero_001") String username,
            ToolContext toolContext) {
        String operator = operatorOf(toolContext);
        log.info("[写工具] unbanPlayer 入参 username={}（模型填） operator={}（服务端 context 注入）",
                username, operator);

        PlayerResponse found = playerService.findByUsername(username).orElse(null);
        if (found == null) {
            return "未找到登录名为 " + username + " 的玩家，未执行任何解封。请核对登录名。";
        }
        try {
            playerService.unban(found.id(), operator);
            log.info("[写工具] unbanPlayer 成功 id={} username={} operator={}", found.id(), username, operator);
            return "已解除玩家 " + username + "（id=" + found.id() + "）的封禁，已记审计。";
        } catch (ConflictException e) {
            log.info("[写工具] unbanPlayer 跳过（本就未封禁） username={} operator={}", username, operator);
            return "玩家 " + username + " 当前不是封禁状态，无需解封。";
        }
    }

    /**
     * 调整玩家货币（写操作、数值累加不幂等）。
     *
     * <p>两道护栏叠在 Service 闸门之上：
     * <ul>
     *   <li>requestId 从 ToolContext 取（客户端生成，模型看不到），配合“动作+参数”组成幂等键，
     *       拦住模型在同一回回答里对同一笔调整手抖调两次（否则就发两份钱）；</li>
     *   <li>幅度上限、余额非负写在 PlayerService，不在这里——因为那是 AI 和人工都要遵守的业务约束。</li>
     * </ul>
     */
    @Tool(description = """
            调整玩家货币（写操作，不可逆）。currency 取 gold 或 diamond；amount 为变动量，正数加、负数减；
            reason 写理由。单次绝对值不得超过 100000、不能扣成负数。仅在运营明确授意并复述目标/币种/金额后调用；
            同一笔调整不要重复调用。""")
    public String adjustCurrency(
            @ToolParam(description = "目标玩家登录名，例如 hero_001") String username,
            @ToolParam(description = "币种：gold 或 diamond") String currency,
            @ToolParam(description = "变动量，正加负减，绝对值≤100000") long amount,
            @ToolParam(description = "调整理由，会写进审计日志") String reason,
            ToolContext toolContext) {
        String operator = operatorOf(toolContext);
        String requestId = contextValue(toolContext, "requestId");
        log.info("[写工具] adjustCurrency 请求 username={} currency={} amount={} operator={} requestId={}",
                username, currency, amount, operator, requestId);

        PlayerResponse found = playerService.findByUsername(username).orElse(null);
        if (found == null) {
            return "未找到登录名为 " + username + " 的玩家，未做任何调整。请核对登录名。";
        }
        // 幂等键：requestId 缺省时退化为不做同-turn 去重（服务端未注入 requestId 的通道，如直接单测）
        String key = requestId == null
                ? null : (requestId + "|adjustCurrency|" + found.id() + "|" + currency + "|" + amount);

        try {
            IdempotencyGuard.Outcome outcome = idempotency.executeOnce(
                    key == null ? "nocache-" + System.nanoTime() : key,
                    () -> {
                        PlayerResponse p = playerService.adjustCurrency(found.id(), currency, amount, operator);
                        return "已将玩家 " + username + " 的 " + currency + " "
                                + (amount > 0 ? "+" : "") + amount + "，现余额 " + balance(p) + "，已记审计。";
                    });
            return outcome.replayed()
                    ? "检测到同一 requestId 的重复调用，本次未重复执行；上次结果：" + outcome.result()
                    : outcome.result();
        } catch (ConflictException e) {
            // 被护栏拒绝（幅度超限/余额不足/币种非法）：回成模型可读的自然语言，鼓励它自我纠正
            log.info("[写工具] adjustCurrency 被拒 username={} reason={}", username, e.getMessage());
            return "调整被拒：" + e.getMessage() + "。未修改数据，请告知运营或调小幅度重试。";
        }
    }

    private static String balance(PlayerResponse p) {
        return "gold=" + p.gold() + ", diamond=" + p.diamond();
    }

    /** 从 ToolContext 取一个字符串值；不存在或空串统一返回 null（表示服务端没注入这个可信上下文）。 */
    private static String contextValue(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object v = toolContext.getContext().get(key);
        return v == null || v.toString().isBlank() ? null : v.toString();
    }

    /**
     * 从 ToolContext 取执行者身份。context 里没有（例如直接单测 new 出来调用、或链路漏塞）时，
     * 标成 "unknown" 而不是回退成某个默认真名——宁可审计里留个显眼的 unknown 待查，
     * 也不要伪造出一个看起来合法的运营账号，把"没有可信来源"这件事藏起来。
     */
    private static String operatorOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return "unknown";
        }
        Object v = toolContext.getContext().get(OPERATOR_KEY);
        return v == null ? "unknown" : v.toString();
    }
}
