package com.gm.demo.service;

import com.gm.demo.entity.domain.Mail;
import com.gm.demo.entity.domain.MailStatus;
import com.gm.demo.entity.domain.MailTargetType;
import com.gm.demo.entity.request.CreateMailRequest;
import com.gm.demo.entity.response.MailResponse;
import com.gm.demo.error.ConflictException;
import com.gm.demo.error.NotFoundException;
import com.gm.demo.repository.MailRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮件业务层：创建草稿 → 发送，两步分离是为了让运营可以先编辑再确认。
 *
 * <p>当前发送只改状态和记时间，不真正投递（没有推送通道）。
 * B2 接 AI 工具时 send 方法就是模型的写操作入口，届时需补幂等键和审计。
 */
@Service
@Transactional(readOnly = true)
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final MailRepository repository;
    private final AuditService audit;

    @Autowired
    public MailService(MailRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public MailResponse createDraft(CreateMailRequest req, String operator) {
        Mail m = Mail.create(req.subject(), req.content(), operator);
        m.setGold(req.gold() == null ? 0 : req.gold());
        m.setDiamond(req.diamond() == null ? 0 : req.diamond());
        if (req.targetType() != null) {
            m.setTargetType(MailTargetType.valueOf(req.targetType()));
        }
        m.setTargetPlayerIds(req.targetPlayerIds());

        Mail saved = repository.save(m);
        log.info("[邮件] 创建草稿 id={} subject={} sender={}", saved.getId(), saved.getSubject(), operator);
        return MailResponse.from(saved);
    }

    /**
     * 发送：只有 DRAFT 才能转 SENT，否则 409。
     * 当前不投递，只标记状态——真实推送留给后续版本。
     */
    @Transactional
    public MailResponse send(Long id, String operator) {
        Mail m = requireEntity(id);
        if (!m.getStatus().canSend()) {
            throw new ConflictException("邮件 id=" + id + " 当前状态 " + m.getStatus() + "，不可重复发送");
        }
        m.setStatus(MailStatus.SENT);
        m.setSentAt(Instant.now());
        Mail saved = repository.save(m);
        audit.record("SEND", "Mail", String.valueOf(id),
                "发送邮件「" + saved.getSubject() + "」", operator);
        log.info("[邮件] 发送 id={} subject={} operator={}", id, saved.getSubject(), operator);
        return MailResponse.from(saved);
    }

    public MailResponse get(Long id) {
        return MailResponse.from(requireEntity(id));
    }

    public Page<MailResponse> list(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pr = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "id"));
        return repository.findAll(pr).map(MailResponse::from);
    }

    public Page<MailResponse> listByStatus(MailStatus status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pr = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "id"));
        return repository.findByStatus(status, pr).map(MailResponse::from);
    }

    private Mail requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Mail", id));
    }
}
