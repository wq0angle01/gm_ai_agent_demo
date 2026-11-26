package com.gm.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gm.demo.entity.domain.SupportTicket;
import com.gm.demo.entity.domain.TicketCategory;
import com.gm.demo.entity.domain.TicketStatus;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.domain.Pageable;

/** 工单 Mapper。多条件组合查询全部走 QueryWrapper，字段名对应 support_ticket 列。 */
@Mapper
public interface TicketRepository
        extends com.baomidou.mybatisplus.core.mapper.BaseMapper<SupportTicket> {

    default Optional<SupportTicket> findById(Long id) {
        return Optional.ofNullable(selectById(id));
    }

    default SupportTicket save(SupportTicket t) {
        if (t.getId() == null) {
            insert(t);
        } else {
            updateById(t);
        }
        return t;
    }

    default void delete(SupportTicket t) {
        deleteById(t.getId());
    }

    default org.springframework.data.domain.Page<SupportTicket> findAll(Pageable pageable) {
        return pageBy(buildStatusCategory(null, null), pageable);
    }

    default org.springframework.data.domain.Page<SupportTicket> findByStatus(
            TicketStatus status, Pageable pageable) {
        return pageBy(buildStatusCategory(status, null), pageable);
    }

    default org.springframework.data.domain.Page<SupportTicket> findByCategory(
            TicketCategory category, Pageable pageable) {
        return pageBy(buildStatusCategory(null, category), pageable);
    }

    default org.springframework.data.domain.Page<SupportTicket> findByStatusAndCategory(
            TicketStatus status, TicketCategory category, Pageable pageable) {
        return pageBy(buildStatusCategory(status, category), pageable);
    }

    default List<SupportTicket> findByAssignee(String assignee) {
        QueryWrapper<SupportTicket> w = new QueryWrapper<>();
        w.eq("assignee", assignee).orderByDesc("id");
        return selectList(w);
    }

    default List<SupportTicket> findByPlayerUsername(String playerUsername) {
        QueryWrapper<SupportTicket> w = new QueryWrapper<>();
        w.eq("player_username", playerUsername).orderByDesc("id");
        return selectList(w);
    }

    default List<SupportTicket> findAll() {
        return selectList(null);
    }

    // ---------- 内部工具 ----------

    private static QueryWrapper<SupportTicket> buildStatusCategory(
            TicketStatus status, TicketCategory category) {
        QueryWrapper<SupportTicket> w = new QueryWrapper<>();
        if (status != null) {
            w.eq("status", status.name());
        }
        if (category != null) {
            w.eq("category", category.name());
        }
        w.orderByDesc("id");
        return w;
    }

    private org.springframework.data.domain.Page<SupportTicket> pageBy(
            QueryWrapper<SupportTicket> w, Pageable pageable) {
        Page<SupportTicket> mp = new Page<>(pageable.getPageNumber() + 1L, pageable.getPageSize());
        IPage<SupportTicket> result = selectPage(mp, w);
        return new org.springframework.data.domain.PageImpl<>(
                result.getRecords(), pageable, result.getTotal());
    }
}
