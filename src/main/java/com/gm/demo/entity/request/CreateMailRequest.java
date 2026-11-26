package com.gm.demo.entity.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建邮件草稿的请求体。subject 和 content 必填，附件和目标玩家可选。 */
public record CreateMailRequest(
        @NotBlank @Size(max = 128) String subject,
        @NotBlank @Size(max = 2000) String content,
        Long gold,
        Long diamond,
        String targetType,
        String targetPlayerIds) {
}
