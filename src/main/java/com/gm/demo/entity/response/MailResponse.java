package com.gm.demo.entity.response;

import com.gm.demo.entity.domain.Mail;
import java.time.Instant;

/** 邮件响应视图。statusLabel / targetTypeLabel 是展示层加工，不属于领域模型。 */
public record MailResponse(
        Long id,
        String subject,
        String content,
        long gold,
        long diamond,
        String targetType,
        String targetTypeLabel,
        String targetPlayerIds,
        String status,
        String statusLabel,
        String sender,
        Instant createdAt,
        Instant sentAt) {

    public static MailResponse from(Mail m) {
        return new MailResponse(
                m.getId(),
                m.getSubject(),
                m.getContent(),
                m.getGold(),
                m.getDiamond(),
                m.getTargetType().name(),
                m.getTargetType().label(),
                m.getTargetPlayerIds(),
                m.getStatus().name(),
                m.getStatus().label(),
                m.getSender(),
                m.getCreatedAt(),
                m.getSentAt());
    }
}
