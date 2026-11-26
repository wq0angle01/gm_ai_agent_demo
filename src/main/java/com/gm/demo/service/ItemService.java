package com.gm.demo.service;

import com.gm.demo.entity.domain.GameItem;
import com.gm.demo.entity.domain.Player;
import com.gm.demo.entity.domain.PlayerItem;
import com.gm.demo.entity.response.GameItemResponse;
import com.gm.demo.entity.response.PlayerItemResponse;
import com.gm.demo.error.NotFoundException;
import com.gm.demo.repository.GameItemRepository;
import com.gm.demo.repository.PlayerItemRepository;
import com.gm.demo.repository.PlayerRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 道具业务层：发放（堆叠合并）+ 查询。
 *
 * <p>发放逻辑：按 playerId + itemId 定位唯一背包条目，已有则累加 count，没有则新建。
 * 超过 maxStack 时截断并记审计 detail（不报错——运营场景下"能发多少发多少"比严格拒绝更实用）。
 *
 * <p>MP 无 @ManyToOne：给定 itemCode 先查 GameItem 拿 itemId，再做主键定位。
 */
@Service
@Transactional(readOnly = true)
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final PlayerRepository playerRepo;
    private final GameItemRepository itemRepo;
    private final PlayerItemRepository playerItemRepo;
    private final AuditService audit;

    @Autowired
    public ItemService(PlayerRepository playerRepo, GameItemRepository itemRepo,
                       PlayerItemRepository playerItemRepo, AuditService audit) {
        this.playerRepo = playerRepo;
        this.itemRepo = itemRepo;
        this.playerItemRepo = playerItemRepo;
        this.audit = audit;
    }

    @Transactional
    public PlayerItemResponse grant(Long playerId, String itemCode, int count, String operator) {
        Player player = playerRepo.findById(playerId)
                .orElseThrow(() -> NotFoundException.of("Player", playerId));
        GameItem item = itemRepo.findByCode(itemCode)
                .orElseThrow(() -> NotFoundException.of("GameItem", itemCode));

        var existing = playerItemRepo.findByPlayerAndItem(playerId, item.getId());
        PlayerItem pi;
        if (existing.isPresent()) {
            pi = existing.get();
            int newCount = Math.min(pi.getCount() + count, item.getMaxStack());
            pi.setCount(newCount);
        } else {
            pi = PlayerItem.create(playerId, item.getId(), Math.min(count, item.getMaxStack()));
        }

        PlayerItem saved = playerItemRepo.save(pi);
        String detail = "发放 %s×%d 给 %s".formatted(item.getName(), count, player.getUsername());
        audit.record("GRANT", "PlayerItem", String.valueOf(saved.getId()), detail, operator);
        log.info("[道具] {} operator={}", detail, operator);
        return PlayerItemResponse.of(saved, item);
    }

    /** 列表用 Mapper 的 JOIN 视图一次拿扁平行，避免 N+1。 */
    public List<PlayerItemResponse> listPlayerItems(Long playerId) {
        return playerItemRepo.listViewByPlayerId(playerId).stream()
                .map(PlayerItemResponse::fromView)
                .toList();
    }

    public List<GameItemResponse> listCatalog() {
        return itemRepo.selectList(null).stream()
                .map(GameItemResponse::from)
                .toList();
    }
}
