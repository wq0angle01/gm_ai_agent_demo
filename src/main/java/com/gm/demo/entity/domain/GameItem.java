package com.gm.demo.entity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 道具目录（模板）。定义"世界上存在哪些道具"，不记录谁持有。
 *
 * <p>code 是业务唯一标识（如 POTION_HP），发放时按 code 查找而非 id，
 * 这样运营改表、数据库迁移都不影响调用方。
 */
@TableName("game_item")
@Getter
@Setter
public class GameItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private ItemType type = ItemType.MISC;

    private String description;

    /** 最大堆叠数，1 表示不可堆叠（如装备）。 */
    private int maxStack = 99;

    public static GameItem create(String code, String name, ItemType type) {
        GameItem item = new GameItem();
        item.code = code;
        item.name = name;
        item.type = type;
        return item;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GameItem other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
