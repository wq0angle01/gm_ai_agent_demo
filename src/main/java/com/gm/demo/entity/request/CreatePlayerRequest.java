package com.gm.demo.entity.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 创建玩家的请求体。与实体分开定义，避免调用方通过多传 id/status 覆盖已有数据。
 *
 * @param username       登录名，唯一，仅字母数字下划线
 * @param nickname       展示名，可重复
 * @param gold           初始金币
 * @param diamond        初始钻石
 * @param rechargeAmount 初始累计充值额，可为空
 */
public record CreatePlayerRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度必须在 3-32 之间")
        @Pattern(regexp = "[A-Za-z0-9_]+", message = "用户名只能包含字母、数字、下划线")
        String username,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称最长 64 个字符")
        String nickname,

        @NotNull(message = "金币必须提供，可为 0")
        @DecimalMin(value = "0", message = "金币不能为负数")
        Long gold,

        @NotNull(message = "钻石必须提供，可为 0")
        @DecimalMin(value = "0", message = "钻石不能为负数")
        Long diamond,

        @Digits(integer = 10, fraction = 2, message = "充值金额最多 10 位整数、2 位小数")
        @DecimalMin(value = "0", message = "充值金额不能为负数")
        BigDecimal rechargeAmount) {
}
