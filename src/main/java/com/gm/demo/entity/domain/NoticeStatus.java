package com.gm.demo.entity.domain;

/** 公告生命周期。EXPIRED 由查询时动态判定，不单独写操作触发。 */
public enum NoticeStatus {
    DRAFT("草稿"),
    PUBLISHED("已发布"),
    EXPIRED("已过期");

    private final String label;

    NoticeStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canPublish() {
        return this == DRAFT;
    }
}
