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
 * 游戏内公告实体。与邮件的区别：公告面向所有玩家展示，不附带货币奖励。
 *
 * <p>effectiveAt/expireAt 定义可见窗口；expireAt 为 null 表示永久。
 * 查询活跃公告用 NoticeRepository.findActive 的显式 SQL 动态比较时间，不依赖定时任务翻转 status。
 */
@TableName("notice")
@Getter
@Setter
public class Notice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /** 排序优先级，越大越靠前；置顶公告设 100 即可。 */
    private int priority = 0;

    private boolean pinned = false;

    private NoticeStatus status = NoticeStatus.DRAFT;

    private Instant effectiveAt;

    private Instant expireAt;

    private String publisher;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    private Instant publishedAt;

    public static Notice create(String title, String content, String publisher) {
        Notice n = new Notice();
        n.title = title;
        n.content = content;
        n.publisher = publisher;
        return n;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notice other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
