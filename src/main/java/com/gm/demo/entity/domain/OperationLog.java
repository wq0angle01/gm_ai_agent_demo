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
 * 操作审计日志。每条记录回答一个问题：谁、在什么时候、对什么做了什么、成功了没有。
 *
 * <p>设计选择：
 * <ul>
 *   <li>单表扁平，不做 JSON 嵌套——查询时按 operator/resource/action 过滤即可。</li>
 *   <li>resourceId 用 String 而不是 Long——将来可能审计非数字主键的资源。</li>
 *   <li>detail 是人类可读摘要，不是序列化后的请求体——方便运营直接看，也避免敏感字段落盘。</li>
 * </ul>
 */
@TableName("operation_log")
@Getter
@Setter
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 动作类型：CREATE / UPDATE / DELETE / SEND / BAN / ADJUST 等。 */
    private String action;

    /** 资源类别：Player / Mail / Notice / Ticket 等。 */
    private String resource;

    /** 资源标识（通常是数据库 id）。 */
    private String resourceId;

    /** 人类可读摘要，如 "封禁玩家 hero_001，原因：外挂"。 */
    private String detail;

    /** 操作人（来自 X-Operator 或系统标识）。 */
    private String operator;

    /** 操作是否成功。失败的写操作同样要留痕。 */
    private boolean success = true;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    public static OperationLog of(String action, String resource, String resourceId,
                                  String detail, String operator, boolean success) {
        OperationLog log = new OperationLog();
        log.action = action;
        log.resource = resource;
        log.resourceId = resourceId;
        log.detail = detail;
        log.operator = operator;
        log.success = success;
        return log;
    }
}
