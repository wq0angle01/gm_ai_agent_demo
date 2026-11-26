package com.gm.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gm.demo.entity.domain.PlayerItem;
import com.gm.demo.entity.response.PlayerItemView;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 玩家背包 Mapper。
 *
 * <p>MP 无 @ManyToOne，"查背包同时带道具名"靠 listViewByPlayerId 显式 JOIN 拍平到 Map；
 * 单条精确定位用 (playerId, itemId) 走 QueryWrapper 即可。
 */
@Mapper
public interface PlayerItemRepository
        extends com.baomidou.mybatisplus.core.mapper.BaseMapper<PlayerItem> {

    default List<PlayerItem> findByPlayerId(Long playerId) {
        QueryWrapper<PlayerItem> w = new QueryWrapper<>();
        w.eq("player_id", playerId).orderByAsc("id");
        return selectList(w);
    }

    default Optional<PlayerItem> findByPlayerAndItem(Long playerId, Long itemId) {
        QueryWrapper<PlayerItem> w = new QueryWrapper<>();
        w.eq("player_id", playerId).eq("item_id", itemId).last("LIMIT 1");
        return Optional.ofNullable(selectOne(w));
    }

    default PlayerItem save(PlayerItem pi) {
        if (pi.getId() == null) {
            insert(pi);
        } else {
            updateById(pi);
        }
        return pi;
    }

    default void delete(PlayerItem pi) {
        deleteById(pi.getId());
    }

    /**
     * 背包列表 JOIN 视图：一次 SQL 拿齐道具 code/name/type，避免 N+1。
     * 结果列名与 PlayerItemResponse 字段一致，Service 层直接从 Map 取值。
     */
    @Select("""
            SELECT pi.id AS id, gi.code AS itemCode, gi.name AS itemName,
                   gi.type AS itemType, pi.count AS count, pi.obtained_at AS obtainedAt
            FROM player_item pi
            JOIN game_item gi ON pi.item_id = gi.id
            WHERE pi.player_id = #{playerId}
            ORDER BY pi.id ASC
            """)
    List<PlayerItemView> listViewByPlayerId(@Param("playerId") Long playerId);
}
