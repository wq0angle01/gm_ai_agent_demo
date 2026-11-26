package com.gm.demo.entity.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 创建公告草稿的请求体。 */
public record CreateNoticeRequest(
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 2000) String content,
        Integer priority,
        Boolean pinned,
        Instant effectiveAt,
        Instant expireAt) {
}
