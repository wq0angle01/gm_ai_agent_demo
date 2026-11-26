package com.gm.demo.entity.response;

import java.math.BigDecimal;

/** 大 R 排行的一行。 */
public record TopSpenderResponse(String username, String nickname, BigDecimal rechargeAmount) {
}
