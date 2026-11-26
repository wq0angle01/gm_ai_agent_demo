package com.gm.demo.entity.domain;

/** 道具分类。不同类型在运营后台的展示和发放规则可能不同。 */
public enum ItemType {
    EQUIPMENT("装备"),
    CONSUMABLE("消耗品"),
    CURRENCY("货币"),
    MISC("杂物");

    private final String label;

    ItemType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
