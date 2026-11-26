package com.gm.demo.entity.response;

import com.gm.demo.entity.domain.Notice;
import java.time.Instant;

/** 公告响应视图。 */
public record NoticeResponse(
        Long id,
        String title,
        String content,
        int priority,
        boolean pinned,
        String status,
        String statusLabel,
        Instant effectiveAt,
        Instant expireAt,
        String publisher,
        Instant createdAt,
        Instant publishedAt) {

    public static NoticeResponse from(Notice n) {
        return new NoticeResponse(
                n.getId(), n.getTitle(), n.getContent(),
                n.getPriority(), n.isPinned(),
                n.getStatus().name(), n.getStatus().label(),
                n.getEffectiveAt(), n.getExpireAt(),
                n.getPublisher(), n.getCreatedAt(), n.getPublishedAt());
    }
}
