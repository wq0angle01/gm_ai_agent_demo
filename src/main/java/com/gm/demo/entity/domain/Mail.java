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
 * 运营邮件实体，映射 mail 表。覆盖补偿发放和全服通知两种场景。
 *
 * <p>targetPlayerIds 用逗号分隔而不是关联表：当前阶段邮件不超 100 收件人，
 * 等真正需要"按玩家查未读"时再拆 PlayerMail，避免过度设计。
 */
@TableName("mail")
@Getter
@Setter
public class Mail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String subject;

    private String content;

    /** 附件金币，0 表示无附件。 */
    private long gold = 0;

    private long diamond = 0;

    private MailTargetType targetType = MailTargetType.SPECIFIED;

    /** 逗号分隔的玩家 id，targetType=ALL 时留空。 */
    private String targetPlayerIds;

    private MailStatus status = MailStatus.DRAFT;

    /** 操作人，来自请求头 X-Operator。 */
    private String sender;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    /** 发送时间，DRAFT 阶段为 null。 */
    private Instant sentAt;

    public static Mail create(String subject, String content, String sender) {
        Mail m = new Mail();
        m.subject = subject;
        m.content = content;
        m.sender = sender;
        return m;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Mail other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
