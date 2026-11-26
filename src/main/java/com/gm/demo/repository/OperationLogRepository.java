package com.gm.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gm.demo.entity.domain.OperationLog;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.domain.Pageable;

/**
 * 审计日志 Mapper。
 *
 * <p>search 用 QueryWrapper 的三个可选条件而不是 @Select 拼字符串——避免 SQL 注入也免维护空判分支。
 * deleteByOperator 用 @Delete 显式 SQL，测试里靠它做数据清理。
 */
@Mapper
public interface OperationLogRepository
        extends com.baomidou.mybatisplus.core.mapper.BaseMapper<OperationLog> {

    default org.springframework.data.domain.Page<OperationLog> search(
            String operator, String action, String resource, Pageable pageable) {
        QueryWrapper<OperationLog> w = new QueryWrapper<>();
        if (operator != null && !operator.isEmpty()) {
            w.eq("operator", operator);
        }
        if (action != null && !action.isEmpty()) {
            w.eq("action", action);
        }
        if (resource != null && !resource.isEmpty()) {
            w.eq("resource", resource);
        }
        w.orderByDesc("id");
        Page<OperationLog> mp = new Page<>(pageable.getPageNumber() + 1L, pageable.getPageSize());
        IPage<OperationLog> result = selectPage(mp, w);
        return new org.springframework.data.domain.PageImpl<>(
                result.getRecords(), pageable, result.getTotal());
    }

    @Delete("DELETE FROM operation_log WHERE operator = #{operator}")
    int deleteByOperator(@Param("operator") String operator);

    default List<OperationLog> findAll() {
        return selectList(null);
    }

    /** 审计表只写不改，save 只需要 insert 分支。 */
    default OperationLog save(OperationLog entry) {
        insert(entry);
        return entry;
    }
}
