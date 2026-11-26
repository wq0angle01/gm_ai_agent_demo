package com.gm.demo.entity.domain;

/** 玩家账号状态。 */
public enum PlayerStatus {

    NORMAL("正常"),
    BANNED("封禁"),
    DISABLED("停用");

    private final String label;

    PlayerStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 状态语义集中在枚举里，避免各处写 status == NORMAL。 */
    public boolean canLogin() {
        return this == NORMAL;
    }
}
