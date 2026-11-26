package com.gm.demo.entity.response;

import com.gm.demo.entity.domain.Player;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 玩家响应视图。与实体分开，实体字段变动不会直接成为对外契约。
 * statusLabel / loginAllowed 是展示层的加工结果，不属于领域模型。
 */
public record PlayerResponse(
        Long id,
        String username,
        String nickname,
        int level,
        int vip,
        long gold,
        long diamond,
        BigDecimal rechargeAmount,
        String status,
        String statusLabel,
        boolean loginAllowed,
        Instant createdAt,
        Instant lastLoginAt) {

    public static PlayerResponse from(Player p) {
        return new PlayerResponse(
                p.getId(),
                p.getUsername(),
                p.getNickname(),
                p.getLevel(),
                p.getVip(),
                p.getGold(),
                p.getDiamond(),
                p.getRechargeAmount(),
                p.getStatus().name(),
                p.getStatus().label(),
                p.getStatus().canLogin(),
                p.getCreatedAt(),
                p.getLastLoginAt());
    }
}
