package com.gm.demo.entity.domain;

/** 工单状态机：OPEN → ASSIGNED → PROCESSING → RESOLVED → CLOSED，只允许单向流转。 */
public enum TicketStatus {
    OPEN("待受理"),
    ASSIGNED("已指派"),
    PROCESSING("处理中"),
    RESOLVED("已解决"),
    CLOSED("已关闭");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
