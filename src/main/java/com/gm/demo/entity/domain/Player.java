package com.gm.demo.entity.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 玩家账号实体，映射 player 表。
 *
 * <p>MP 只映射扁平列，驼峰↔下划线自动互转；时间戳由 MetaObjectHandler 填充，
 * 因此不需要 Hibernate 的 {@code @PrePersist}。列长度/唯一约束由 RDS DDL 负责，代码不再声明。
 */
@TableName("player")
@Getter
@Setter
public class Player {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，业务唯一键；唯一性写在 DDL 上而不是只靠代码判断。 */
    private String username;

    /** 展示名，可重复、可中文。 */
    private String nickname;

    private int level = 1;

    /** VIP 等级，0 表示非付费。 */
    private int vip = 0;

    private long gold = 0;

    private long diamond = 0;

    /** 累计充值金额：金额一律 BigDecimal，double 会有 0.1+0.2 的精度问题。 */
    private BigDecimal rechargeAmount = BigDecimal.ZERO;

    /** 枚举按 name() 存取，由 application.properties 里 default-enum-type-handler 指定。 */
    private PlayerStatus status = PlayerStatus.NORMAL;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    /** 最近登录时间，可为空。 */
    private Instant lastLoginAt;

    /** 静态工厂而不是全参构造，避免靠参数顺序对齐。 */
    public static Player create(String username, String nickname) {
        Player p = new Player();
        p.username = username;
        p.nickname = nickname;
        return p;
    }

    /** 已落库才比 id；未落库时 id 为 null，只能比引用。 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Player other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Player#" + id + "(" + username + ", " + status + ")";
    }
}
