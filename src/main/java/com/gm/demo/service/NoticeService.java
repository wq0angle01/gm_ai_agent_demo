package com.gm.demo.service;

import com.gm.demo.entity.domain.Notice;
import com.gm.demo.entity.domain.NoticeStatus;
import com.gm.demo.entity.request.CreateNoticeRequest;
import com.gm.demo.entity.response.NoticeResponse;
import com.gm.demo.error.ConflictException;
import com.gm.demo.error.NotFoundException;
import com.gm.demo.repository.NoticeRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公告业务层：草稿 → 发布。
 *
 * <p>活跃公告查询不依赖定时任务翻转 status，而是在查询时动态比较时间窗口。
 */
@Service
@Transactional(readOnly = true)
public class NoticeService {

    private static final Logger log = LoggerFactory.getLogger(NoticeService.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final NoticeRepository repository;
    private final AuditService audit;

    @Autowired
    public NoticeService(NoticeRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public NoticeResponse createDraft(CreateNoticeRequest req, String operator) {
        Notice n = Notice.create(req.title(), req.content(), operator);
        n.setPriority(req.priority() == null ? 0 : req.priority());
        n.setPinned(req.pinned() != null && req.pinned());
        n.setEffectiveAt(req.effectiveAt());
        n.setExpireAt(req.expireAt());

        Notice saved = repository.save(n);
        log.info("[公告] 创建草稿 id={} title={} publisher={}", saved.getId(), saved.getTitle(), operator);
        return NoticeResponse.from(saved);
    }

    @Transactional
    public NoticeResponse publish(Long id, String operator) {
        Notice n = requireEntity(id);
        if (!n.getStatus().canPublish()) {
            throw new ConflictException("公告 id=" + id + " 当前状态 " + n.getStatus() + "，不可重复发布");
        }
        n.setStatus(NoticeStatus.PUBLISHED);
        n.setPublishedAt(Instant.now());
        if (n.getEffectiveAt() == null) {
            n.setEffectiveAt(n.getPublishedAt());
        }
        Notice saved = repository.save(n);
        audit.record("PUBLISH", "Notice", String.valueOf(id),
                "发布公告「" + saved.getTitle() + "」", operator);
        log.info("[公告] 发布 id={} title={} operator={}", id, saved.getTitle(), operator);
        return NoticeResponse.from(saved);
    }

    public NoticeResponse get(Long id) {
        return NoticeResponse.from(requireEntity(id));
    }

    public Page<NoticeResponse> list(NoticeStatus status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pr = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "id"));
        if (status != null) {
            return repository.findByStatus(status, pr).map(NoticeResponse::from);
        }
        return repository.findAll(pr).map(NoticeResponse::from);
    }

    public List<NoticeResponse> findActive() {
        return repository.findActive(Instant.now()).stream()
                .map(NoticeResponse::from)
                .toList();
    }

    private Notice requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Notice", id));
    }
}
