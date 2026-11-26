package com.gm.demo.entity.domain;

/** 邮件生命周期状态。DRAFT 可以反复编辑，SENT 不可撤回（RECALLED 留给后续版本）。 */
public enum MailStatus {
    DRAFT("草稿"),
    SENT("已发送"),
    RECALLED("已撤回");

    private final String label;

    MailStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canSend() {
        return this == DRAFT;
    }
}
