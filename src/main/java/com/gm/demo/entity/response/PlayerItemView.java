package com.gm.demo.entity.response;

import java.time.Instant;
import lombok.Data;

/**
 * PlayerItem JOIN GameItem 的扁平视图（Mapper 直接映射，非实体）。
 *
 * <p>MP 的 @Select 返回 Map 时 Instant 列拿到的是 LocalDateTime/Timestamp，
 * 靠字段类型触发 MyBatis 内置 InstantTypeHandler 才能稳定转成 Instant。
 */
@Data
public class PlayerItemView {
    private Long id;
    private String itemCode;
    private String itemName;
    private String itemType;
    private Integer count;
    private Instant obtainedAt;
}
