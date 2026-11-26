package com.gm.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gm.demo.entity.domain.GameItem;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/** 道具目录 Mapper。 */
@Mapper
public interface GameItemRepository extends com.baomidou.mybatisplus.core.mapper.BaseMapper<GameItem> {

    default Optional<GameItem> findByCode(String code) {
        QueryWrapper<GameItem> w = new QueryWrapper<>();
        w.eq("code", code).last("LIMIT 1");
        return Optional.ofNullable(selectOne(w));
    }
}
