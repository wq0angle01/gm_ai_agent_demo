package com.gm.demo.tool;

import com.gm.demo.entity.response.PlayerResponse;
import com.gm.demo.service.PlayerService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 给模型查询玩家用的只读工具集。
 *
 * <p>返回一行纯文本，不返回 PlayerResponse 的 JSON：模型只要能读懂这句话就够了，
 * 而整个实体会把十几个字段连同样式标记一起塞进上下文，白烧 token 还扩大幻觉面。
 *
 * <p>工具方法保持只读。写操作（封号、调货币）要等审计留痕和参数上限做完才能开放，
 * 否则模型一句话就能改数据，出事查不到是谁授意的。
 */
@Component
public class PlayerQueryTool {

    /** 数据库存的是时刻，Instant.toString 会给出 UTC；运营看的是东八区，差 8 小时会被当成假数据。 */
    private static final DateTimeFormatter LOGIN_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private final PlayerService playerService;

    @Autowired
    public PlayerQueryTool(PlayerService playerService) {
        this.playerService = playerService;
    }

    @Tool(description = """
            查单个玩家的档案与货币余额。登录名区分大小写，形如 hero_001。\
            问题涉及某个具体玩家的实际数值时必须调用本工具，不要凭猜测回答。""")
    public String findPlayerByUsername(
            @ToolParam(description = "玩家登录名，例如 hero_001") String username) {
        return playerService.findByUsername(username)
                .map(PlayerQueryTool::describe)
                .orElse("未找到登录名为 " + username + " 的玩家。不要编造该玩家的数据，如实说明查不到并建议核对登录名。");
    }

    /** 字段名写在值前面，模型不必再猜哪个数是金币、哪个是钻石。 */
    private static String describe(PlayerResponse p) {
        return "登录名=%s，昵称=%s，等级=%d，VIP=%d，金币=%d，钻石=%d，累计充值=%s元，状态=%s，最近登录=%s"
                .formatted(
                        p.username(),
                        p.nickname(),
                        p.level(),
                        p.vip(),
                        p.gold(),
                        p.diamond(),
                        p.rechargeAmount().toPlainString(),
                        p.statusLabel(),
                        p.lastLoginAt() == null ? "从未登录" : LOGIN_TIME.format(p.lastLoginAt()));
    }
}
