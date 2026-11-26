package com.gm.demo.entity.request;

import com.gm.demo.entity.domain.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建工单请求。 */
public record CreateTicketRequest(
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 64) String playerUsername,
        TicketCategory category) {
}
