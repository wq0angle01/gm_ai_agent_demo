package com.gm.demo.entity.response;

import com.gm.demo.entity.domain.SupportTicket;
import java.time.Instant;

/** 工单响应视图。 */
public record TicketResponse(
        Long id,
        String title,
        String description,
        String playerUsername,
        String category,
        String categoryLabel,
        String status,
        String statusLabel,
        String assignee,
        String resolution,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {

    public static TicketResponse from(SupportTicket t) {
        return new TicketResponse(
                t.getId(), t.getTitle(), t.getDescription(),
                t.getPlayerUsername(),
                t.getCategory().name(), t.getCategory().label(),
                t.getStatus().name(), t.getStatus().label(),
                t.getAssignee(), t.getResolution(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getClosedAt());
    }
}
