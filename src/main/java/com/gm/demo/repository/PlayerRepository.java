package com.gm.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gm.demo.entity.domain.Player;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.domain.Pageable;

/**
 * 玩家 Mapper。MP 的 BaseMapper 提供 selectById/selectList/insert/updateById/deleteById，
 * 这里保留旧 Spring Data 命名（findById/save/delete...）作为 default 方法，Service 层零改动。
 *
 * <p>search 的 LIKE 用命名参数 #{kw}，与 JPA 一样是 JDBC 占位符，不存在拼接注入。
 * overviewStats 用 Map 接扁平行，避免定义一次性 POJO。
 */
@Mapper
public interface PlayerRepository extends com.baomidou.mybatisplus.core.mapper.BaseMapper<Player> {

    // ---------- Spring Data 兼容 default 方法 ----------

    default Optional<Player> findById(Long id) {
        return Optional.ofNullable(selectById(id));
    }

    default Player save(Player p) {
        if (p.getId() == null) {
            insert(p);
        } else {
            updateById(p);
        }
        return p;
    }

    default void delete(Player p) {
        deleteById(p.getId());
    }

    default List<Player> findAll() {
        return selectList(null);
    }

    default boolean existsByUsername(String username) {
        QueryWrapper<Player> w = new QueryWrapper<>();
        w.eq("username", username);
        return exists(w);
    }

    default Optional<Player> findByUsername(String username) {
        QueryWrapper<Player> w = new QueryWrapper<>();
        w.eq("username", username).last("LIMIT 1");
        return Optional.ofNullable(selectOne(w));
    }

    /** 关键字分页搜索：kw 为空跳过 LIKE；lower() 让 username/nickname 匹配不区分大小写。 */
    default org.springframework.data.domain.Page<Player> search(String kw, Pageable pageable) {
        QueryWrapper<Player> w = new QueryWrapper<>();
        if (kw != null && !kw.isEmpty()) {
            String like = "%" + kw.toLowerCase() + "%";
            w.and(q -> q.apply("LOWER(username) LIKE {0}", like)
                    .or().apply("LOWER(nickname) LIKE {0}", like));
        }
        w.orderByDesc("id");
        Page<Player> mp = new Page<>(pageable.getPageNumber() + 1L, pageable.getPageSize());
        IPage<Player> result = selectPage(mp, w);
        return new org.springframework.data.domain.PageImpl<>(
                result.getRecords(), pageable, result.getTotal());
    }

    // ---------- 报表聚合：显式 SQL ----------

    /**
     * 全服概览：一次查询拿齐总数/封禁数/付费数/充值总额。
     * COALESCE 保证空表时聚合返回 0 而不是 null；返回列名和 ReportService 里 map.get 的 key 一一对应。
     */
    @Select("""
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(CASE WHEN status = 'BANNED' THEN 1 ELSE 0 END), 0) AS banned,
                   COALESCE(SUM(CASE WHEN recharge_amount > 0 THEN 1 ELSE 0 END), 0) AS paying,
                   COALESCE(SUM(recharge_amount), 0) AS totalRecharge
            FROM player
            """)
    Map<String, Object> overviewStats();

    /** 等级分布：按 level 分组计数，升序方便直接画直方图。 */
    @Select("SELECT level AS level, COUNT(*) AS cnt FROM player GROUP BY level ORDER BY level ASC")
    List<Map<String, Object>> levelDistribution();

    /** 大 R 排行：LIMIT 下推到 SQL，topN 已在 Service 做过 1–100 护栏。 */
    @Select("""
            SELECT username, nickname, recharge_amount AS rechargeAmount
            FROM player
            WHERE recharge_amount > 0
            ORDER BY recharge_amount DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> topSpenders(@org.apache.ibatis.annotations.Param("limit") int limit);
}
