package com.gm.demo.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gm.demo.entity.domain.Player;
import com.gm.demo.entity.domain.PlayerStatus;
import com.gm.demo.repository.PlayerRepository;
import com.gm.demo.service.AuditService;
import com.gm.demo.service.PlayerService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

/**
 * 写工具纯单测：不起容器、不发网络、不进模型。护栏的正确性不该靠调模型验证——
 * 那既花钱又不确定。这里直接 new 出被测对象，用 Mockito 盯住"审计收到的 operator 到底是什么"。
 */
class GmOpsWriteToolTest {

    private final PlayerRepository repository = mock(PlayerRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final GmOpsWriteTool tool =
            new GmOpsWriteTool(new PlayerService(repository, audit), new IdempotencyGuard());

    private static Player player(Long id, String username, PlayerStatus status) {
        Player p = Player.create(username, "测试玩家");
        p.setId(id);
        p.setStatus(status);
        return p;
    }

    private static Player richPlayer(Long id, String username, long gold) {
        Player p = player(id, username, PlayerStatus.NORMAL);
        p.setGold(gold);
        return p;
    }

    @Test
    void banUsesOperatorFromToolContextNotFromModel() {
        Player normal = player(7L, "hero_001", PlayerStatus.NORMAL);
        when(repository.findByUsername("hero_001")).thenReturn(Optional.of(normal));
        when(repository.findById(7L)).thenReturn(Optional.of(normal));
        when(repository.save(any(Player.class))).thenAnswer(i -> i.getArgument(0));

        String out = tool.banPlayer("hero_001", new ToolContext(Map.of("operator", "ops_zhang")));

        assertThat(out).contains("已封禁").contains("hero_001");
        // operator 落进审计的是 context 里的值，模型没有途径替换它
        verify(audit).record(eq("BAN"), eq("Player"), eq("7"), anyString(), eq("ops_zhang"));
    }

    @Test
    void banAlreadyBannedIsIdempotentAndSkipsAudit() {
        Player banned = player(7L, "hero_001", PlayerStatus.BANNED);
        when(repository.findByUsername("hero_001")).thenReturn(Optional.of(banned));
        when(repository.findById(7L)).thenReturn(Optional.of(banned));

        String out = tool.banPlayer("hero_001", new ToolContext(Map.of("operator", "ops_zhang")));

        assertThat(out).contains("已是封禁状态");
        verify(audit, never()).record(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void banUnknownPlayerTouchesNothingAndSaysSo() {
        when(repository.findByUsername("ghost_999")).thenReturn(Optional.empty());

        String out = tool.banPlayer("ghost_999", new ToolContext(Map.of("operator", "ops_zhang")));

        assertThat(out).contains("未找到").contains("未执行任何封禁");
        verifyNoInteractions(audit);
    }

    @Test
    void missingOperatorBecomesExplicitUnknownInsteadOfAFakeName() {
        Player normal = player(7L, "hero_001", PlayerStatus.NORMAL);
        when(repository.findByUsername("hero_001")).thenReturn(Optional.of(normal));
        when(repository.findById(7L)).thenReturn(Optional.of(normal));
        when(repository.save(any(Player.class))).thenAnswer(i -> i.getArgument(0));

        // context 里漏塞 operator：宁可审计留 "unknown" 待查，也不伪造一个看着合法的运营账号
        tool.banPlayer("hero_001", new ToolContext(Map.of()));

        verify(audit).record(eq("BAN"), eq("Player"), eq("7"), anyString(), eq("unknown"));
    }

    // ---------- adjustCurrency 护栏 ----------

    @Test
    void adjustOverCapIsRejectedAndWritesNothing() {
        Player p = richPlayer(7L, "hero_001", 100L);
        when(repository.findByUsername("hero_001")).thenReturn(Optional.of(p));

        String out = tool.adjustCurrency(
                "hero_001", "gold", 200_000L, "发补偿",
                new ToolContext(Map.of("operator", "ops_zhang", "requestId", "req-1")));

        assertThat(out).contains("调整被拒").contains("上限");
        verifyNoInteractions(audit);
    }

    @Test
    void adjustBelowZeroIsRejectedAndTellsModelCurrentBalance() {
        Player p = richPlayer(7L, "hero_001", 30L);
        when(repository.findByUsername("hero_001")).thenReturn(Optional.of(p));
        when(repository.findById(7L)).thenReturn(Optional.of(p));

        String out = tool.adjustCurrency(
                "hero_001", "gold", -100L, "扣款",
                new ToolContext(Map.of("operator", "ops_zhang", "requestId", "req-2")));

        assertThat(out).contains("调整被拒").contains("余额不足");
        verifyNoInteractions(audit);
    }

    @Test
    void adjustSuccessAuditsWithOperatorFromContext() {
        Player p = richPlayer(7L, "hero_001", 100L);
        when(repository.findByUsername("hero_001")).thenReturn(Optional.of(p));
        when(repository.findById(7L)).thenReturn(Optional.of(p));
        when(repository.save(any(Player.class))).thenAnswer(i -> i.getArgument(0));

        String out = tool.adjustCurrency(
                "hero_001", "gold", 50L, "活动奖励",
                new ToolContext(Map.of("operator", "ops_zhang", "requestId", "req-3")));

        assertThat(out).contains("已将玩家").contains("+50");
        verify(audit).record(eq("ADJUST_CURRENCY"), eq("Player"), eq("7"), anyString(), eq("ops_zhang"));
    }

    /** 同一个 requestId 重复调同一笔，只真执行一次——这就是调货币区别于 ban 的关键护栏。 */
    @Test
    void duplicateRequestIdOnlyAdjustsOnce() {
        Player p = richPlayer(7L, "hero_001", 100L);
        when(repository.findByUsername("hero_001")).thenReturn(Optional.of(p));
        when(repository.findById(7L)).thenReturn(Optional.of(p));
        when(repository.save(any(Player.class))).thenAnswer(i -> i.getArgument(0));
        ToolContext ctx = new ToolContext(Map.of("operator", "ops_zhang", "requestId", "req-dup"));

        tool.adjustCurrency("hero_001", "gold", 500L, "补发奖励", ctx);
        String second = tool.adjustCurrency("hero_001", "gold", 500L, "补发奖励", ctx);

        assertThat(second).contains("重复调用").contains("本次未重复执行");
        verify(audit, times(1))
                .record(eq("ADJUST_CURRENCY"), eq("Player"), eq("7"), anyString(), eq("ops_zhang"));
    }
}
