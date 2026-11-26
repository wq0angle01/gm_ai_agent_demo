package com.gm.demo.entity.domain;

/** 邮件投递范围。ALL 是全服广播，SPECIFIED 是指定玩家列表。 */
public enum MailTargetType {
    ALL("全服"),
    SPECIFIED("指定玩家");

    private final String label;

    MailTargetType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
