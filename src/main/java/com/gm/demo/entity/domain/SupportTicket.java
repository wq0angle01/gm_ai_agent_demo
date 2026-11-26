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
 * 客服工单实体。状态机只允许单向流转（OPEN→ASSIGNED→PROCESSING→RESOLVED→CLOSED），
 * 非法跳转由 Service 层校验并抛 ConflictException。
 *
 * <p>playerUsername 用字符串而不是外键：工单可能来自未注册玩家或系统自动创建，
 * 不强制关联 Player 表。枚举按 name() 存取，见 application.properties 的 default-enum-type-handler。
 */
@TableName("support_ticket")
@Getter
@Setter
public class SupportTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String playerUsername;

    private TicketCategory category = TicketCategory.OTHER;

    private TicketStatus status = TicketStatus.OPEN;

    private String assignee;

    private String resolution;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /** 关闭时间，状态未到 CLOSED 时为 null。 */
    private Instant closedAt;

    public static SupportTicket create(String title, String description,
                                       String playerUsername, TicketCategory category) {
        SupportTicket t = new SupportTicket();
        t.title = title;
        t.description = description;
        t.playerUsername = playerUsername;
        t.category = category;
        return t;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SupportTicket other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
