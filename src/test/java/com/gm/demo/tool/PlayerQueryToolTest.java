package com.gm.demo.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gm.demo.entity.domain.Player;
import com.gm.demo.repository.PlayerRepository;
import com.gm.demo.service.AuditService;
import com.gm.demo.service.PlayerService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 工具方法的纯单测：不起容器、不连数据库、不发网络请求。
 * 能做到这点全靠整条链都是构造器注入，直接 new 就拼得出被测对象。
 */
class PlayerQueryToolTest {

    private final PlayerRepository repository = mock(PlayerRepository.class);
    private final PlayerQueryTool tool = new PlayerQueryTool(new PlayerService(repository, mock(AuditService.class)));

    private Player player(String username) {
        Player p = Player.create(username, "测试玩家");
        p.setGold(88L);
        p.setDiamond(99999L);
        p.setRechargeAmount(new BigDecimal("64800.00"));
        return p;
    }

    @Test
    void foundPlayerIsDescribedWithEveryNumberTheModelNeeds() {
        when(repository.findByUsername("whale_002")).thenReturn(Optional.of(player("whale_002")));

        String text = tool.findPlayerByUsername("whale_002");

        assertThat(text).contains("登录名=whale_002", "金币=88", "钻石=99999", "累计充值=64800.00元");
    }

    /** 查不到必须说查不到：返回体里出现任何数值，模型就会照着编。 */
    @Test
    void missingPlayerYieldsAnExplicitNotFoundInsteadOfAGuess() {
        when(repository.findByUsername("ghost_999")).thenReturn(Optional.empty());

        assertThat(tool.findPlayerByUsername("ghost_999"))
                .contains("未找到登录名为 ghost_999 的玩家")
                .doesNotContain("金币", "钻石");
    }

    /** 库里存的是时刻，直接 toString 会给 UTC，运营看的是东八区，差 8 小时。 */
    @Test
    void lastLoginIsRenderedInBeijingTime() {
        Player p = player("whale_002");
        p.setLastLoginAt(Instant.parse("2026-11-21T00:40:00Z"));
        when(repository.findByUsername("whale_002")).thenReturn(Optional.of(p));

        assertThat(tool.findPlayerByUsername("whale_002")).contains("最近登录=2026-11-21 08:40");
    }

    @Test
    void neverLoggedInPlayerIsNotRenderedAsNull() {
        when(repository.findByUsername("newbie_003")).thenReturn(Optional.of(player("newbie_003")));

        assertThat(tool.findPlayerByUsername("newbie_003")).contains("最近登录=从未登录").doesNotContain("null");
    }
}
