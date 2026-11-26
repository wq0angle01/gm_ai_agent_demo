package com.gm.demo.entity.domain;

/** 工单分类，B3 分诊 Agent 的输出就是这个枚举。 */
public enum TicketCategory {
    BUG("功能异常"),
    CHEAT("作弊举报"),
    PAYMENT("充值问题"),
    SUGGESTION("建议反馈"),
    OTHER("其他");

    private final String label;

    TicketCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
