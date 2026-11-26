package com.gm.demo.entity.response;

import java.math.BigDecimal;

/** 全服概览报表。 */
public record ReportOverviewResponse(
        long totalPlayers,
        long bannedPlayers,
        long payingPlayers,
        BigDecimal totalRecharge,
        BigDecimal arpu) {
}
