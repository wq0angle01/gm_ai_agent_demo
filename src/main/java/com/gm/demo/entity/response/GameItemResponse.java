package com.gm.demo.entity.response;

import com.gm.demo.entity.domain.GameItem;

/** 道具目录响应。 */
public record GameItemResponse(
        Long id,
        String code,
        String name,
        String type,
        String typeLabel,
        String description,
        int maxStack) {

    public static GameItemResponse from(GameItem g) {
        return new GameItemResponse(
                g.getId(), g.getCode(), g.getName(),
                g.getType().name(), g.getType().label(),
                g.getDescription(), g.getMaxStack());
    }
}
