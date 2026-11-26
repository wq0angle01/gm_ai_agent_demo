package com.gm.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gm.demo.entity.domain.Notice;
import com.gm.demo.entity.domain.NoticeStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.domain.Pageable;

/** 公告 Mapper。findActive 保留显式 SQL——多条件动态时间比较用 Wrapper 反而更啰嗦。 */
@Mapper
public interface NoticeRepository extends com.baomidou.mybatisplus.core.mapper.BaseMapper<Notice> {

    default Optional<Notice> findById(Long id) {
        return Optional.ofNullable(selectById(id));
    }

    default Notice save(Notice n) {
        if (n.getId() == null) {
            insert(n);
        } else {
            updateById(n);
        }
        return n;
    }

    default void delete(Notice n) {
        deleteById(n.getId());
    }

    default org.springframework.data.domain.Page<Notice> findAll(Pageable pageable) {
        QueryWrapper<Notice> w = new QueryWrapper<>();
        w.orderByDesc("pinned").orderByDesc("priority").orderByDesc("published_at");
        Page<Notice> mp = new Page<>(pageable.getPageNumber() + 1L, pageable.getPageSize());
        IPage<Notice> result = selectPage(mp, w);
        return new org.springframework.data.domain.PageImpl<>(
                result.getRecords(), pageable, result.getTotal());
    }

    default org.springframework.data.domain.Page<Notice> findByStatus(NoticeStatus status, Pageable pageable) {
        QueryWrapper<Notice> w = new QueryWrapper<>();
        w.eq("status", status.name())
                .orderByDesc("pinned").orderByDesc("priority").orderByDesc("published_at");
        Page<Notice> mp = new Page<>(pageable.getPageNumber() + 1L, pageable.getPageSize());
        IPage<Notice> result = selectPage(mp, w);
        return new org.springframework.data.domain.PageImpl<>(
                result.getRecords(), pageable, result.getTotal());
    }

    default Optional<Notice> findByTitle(String title) {
        QueryWrapper<Notice> w = new QueryWrapper<>();
        w.eq("title", title).last("LIMIT 1");
        return Optional.ofNullable(selectOne(w));
    }

    /**
     * 当前可见的公告：状态 PUBLISHED 且时间在 [effectiveAt, expireAt) 窗口内。
     * MySQL 直接支持 NULL-safe 的 OR 条件，不需要数据库方言。
     */
    @Select("""
            SELECT * FROM notice
            WHERE status = 'PUBLISHED'
              AND (effective_at IS NULL OR effective_at <= #{now})
              AND (expire_at IS NULL OR expire_at > #{now})
            ORDER BY pinned DESC, priority DESC, published_at DESC
            """)
    List<Notice> findActive(@Param("now") Instant now);
}
