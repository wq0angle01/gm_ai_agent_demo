package com.gm.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gm.demo.entity.domain.Mail;
import com.gm.demo.entity.domain.MailStatus;
import java.util.Collection;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.domain.Pageable;

/** 邮件 Mapper。派生查询用 QueryWrapper，不写 SQL。 */
@Mapper
public interface MailRepository extends com.baomidou.mybatisplus.core.mapper.BaseMapper<Mail> {

    default Optional<Mail> findById(Long id) {
        return Optional.ofNullable(selectById(id));
    }

    default Mail save(Mail m) {
        if (m.getId() == null) {
            insert(m);
        } else {
            updateById(m);
        }
        return m;
    }

    default void delete(Mail m) {
        deleteById(m.getId());
    }

    default void deleteAllById(Collection<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            deleteBatchIds(ids);
        }
    }

    default org.springframework.data.domain.Page<Mail> findAll(Pageable pageable) {
        QueryWrapper<Mail> w = new QueryWrapper<>();
        w.orderByDesc("id");
        Page<Mail> mp = new Page<>(pageable.getPageNumber() + 1L, pageable.getPageSize());
        IPage<Mail> result = selectPage(mp, w);
        return new org.springframework.data.domain.PageImpl<>(
                result.getRecords(), pageable, result.getTotal());
    }

    default org.springframework.data.domain.Page<Mail> findByStatus(MailStatus status, Pageable pageable) {
        QueryWrapper<Mail> w = new QueryWrapper<>();
        w.eq("status", status.name()).orderByDesc("id");
        Page<Mail> mp = new Page<>(pageable.getPageNumber() + 1L, pageable.getPageSize());
        IPage<Mail> result = selectPage(mp, w);
        return new org.springframework.data.domain.PageImpl<>(
                result.getRecords(), pageable, result.getTotal());
    }

    default Optional<Mail> findBySubject(String subject) {
        QueryWrapper<Mail> w = new QueryWrapper<>();
        w.eq("subject", subject).last("LIMIT 1");
        return Optional.ofNullable(selectOne(w));
    }
}
