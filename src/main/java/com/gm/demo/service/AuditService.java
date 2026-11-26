package com.gm.demo.service;

import com.gm.demo.entity.domain.OperationLog;
import com.gm.demo.entity.response.AuditLogResponse;
import com.gm.demo.repository.OperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志服务：记录写操作 + 提供分页查询。
 *
 * <p>record 方法用 REQUIRES_NEW 传播——即使主业务事务回滚，审计仍落盘。
 * 这是运营后台的硬需求：失败的写操作同样要留痕（谁在什么时候试了什么）。
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final OperationLogRepository repository;

    @Autowired
    public AuditService(OperationLogRepository repository) {
        this.repository = repository;
    }

    /** 记录一条审计。REQUIRES_NEW 保证即使外层事务回滚也留痕。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String resource, String resourceId,
                       String detail, String operator, boolean success) {
        OperationLog entry = OperationLog.of(action, resource, resourceId, detail, operator, success);
        repository.save(entry);
        log.debug("[审计] {} {}#{} by={} success={}", action, resource, resourceId, operator, success);
    }

    /** 简化版：默认 success=true。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String resource, String resourceId,
                       String detail, String operator) {
        record(action, resource, resourceId, detail, operator, true);
    }

    public Page<AuditLogResponse> search(String operator, String action, String resource,
                                         int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pr = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "id"));
        return repository.search(operator, action, resource, pr).map(AuditLogResponse::from);
    }
}
