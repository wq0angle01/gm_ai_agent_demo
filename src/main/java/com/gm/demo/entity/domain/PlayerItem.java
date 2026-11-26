package com.gm.demo.entity.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 玩家背包条目。一个玩家对一种道具只有一条记录（唯一约束 player_id + item_id），
 * 发放时合并 count 而不是新增行。
 *
 * <p>MP 没有 @ManyToOne：只存 playerId/itemId 两个外键 ID，展示道具信息靠
 * ItemRepository.listViewByPlayerId 的显式 JOIN SQL 拿扁平视图（见 PlayerItemView）。
 */
@TableName("player_item")
@Getter
@Setter
public class PlayerItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playerId;

    private Long itemId;

    private int count;

    @TableField(fill = FieldFill.INSERT)
    private Instant obtainedAt;

    public static PlayerItem create(Long playerId, Long itemId, int count) {
        PlayerItem pi = new PlayerItem();
        pi.playerId = playerId;
        pi.itemId = itemId;
        pi.count = count;
        return pi;
    }
}
