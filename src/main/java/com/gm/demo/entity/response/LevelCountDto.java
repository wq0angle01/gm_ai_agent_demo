package com.gm.demo.entity.response;

/** 等级分布的一个桶：该等级有多少玩家。 */
public record LevelCountDto(int level, long playerCount) {
}
