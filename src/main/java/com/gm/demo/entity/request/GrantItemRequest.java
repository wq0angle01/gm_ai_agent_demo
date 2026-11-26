package com.gm.demo.entity.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 发放道具请求。 */
public record GrantItemRequest(
        @NotNull Long playerId,
        @NotBlank String itemCode,
        @NotNull @Min(1) Integer count) {
}
