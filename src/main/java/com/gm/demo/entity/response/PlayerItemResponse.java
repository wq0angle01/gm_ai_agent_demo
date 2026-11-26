package com.gm.demo.entity.response;

import java.time.Instant;

/**
 * 玩家背包条目响应。展开道具信息方便前端直接显示，不用二次查询。
 *
 * <p>MP 无懒加载关联，构造两种入口：
 * <ul>
 *   <li>{@link #of(PlayerItem, GameItem)} — grant 时手里已有 GameItem，直接拼；</li>
 *   <li>{@link #fromRow(Map)} — listPlayerItems 走 Mapper.listViewByPlayerId 一次 JOIN 拿扁平 Map。</li>
 * </ul>
 */
public record PlayerItemResponse(
        Long id,
        String itemCode,
        String itemName,
        String itemType,
        int count,
        Instant obtainedAt) {

    public static PlayerItemResponse of(
            com.gm.demo.entity.domain.PlayerItem pi, com.gm.demo.entity.domain.GameItem item) {
        return new PlayerItemResponse(
                pi.getId(),
                item.getCode(),
                item.getName(),
                item.getType().name(),
                pi.getCount(),
                pi.getObtainedAt());
    }

    /** PlayerItemRepository.listViewByPlayerId 的 typed view → response。 */
    public static PlayerItemResponse fromView(PlayerItemView v) {
        return new PlayerItemResponse(
                v.getId(),
                v.getItemCode(),
                v.getItemName(),
                v.getItemType(),
                v.getCount(),
                v.getObtainedAt());
    }
}
