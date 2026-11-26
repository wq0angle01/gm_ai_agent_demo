package com.gm.demo.service;

import com.gm.demo.entity.domain.SupportTicket;
import com.gm.demo.entity.domain.TicketCategory;
import com.gm.demo.entity.domain.TicketStatus;
import com.gm.demo.entity.request.CreateTicketRequest;
import com.gm.demo.entity.response.TicketResponse;
import com.gm.demo.error.ConflictException;
import com.gm.demo.error.NotFoundException;
import com.gm.demo.repository.TicketRepository;
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
 * 工单业务层：状态机 OPEN→ASSIGNED→PROCESSING→RESOLVED→CLOSED 单向流转。
 *
 * <p>每一步都校验当前状态，非法跳转抛 ConflictException（409）。
 * 所有写操作回扣 AuditService 留痕。
 */
@Service
@Transactional(readOnly = true)
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final TicketRepository repository;
    private final AuditService audit;

    @Autowired
    public TicketService(TicketRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest req, String operator) {
        SupportTicket t = SupportTicket.create(
                req.title(), req.description(), req.playerUsername(),
                req.category() != null ? req.category() : TicketCategory.OTHER);
        SupportTicket saved = repository.save(t);
        audit.record("CREATE", "Ticket", String.valueOf(saved.getId()),
                "创建工单「" + saved.getTitle() + "」", operator);
        log.info("[工单] 创建 id={} title={}", saved.getId(), saved.getTitle());
        return TicketResponse.from(saved);
    }

    /** OPEN → ASSIGNED：指派处理人。 */
    @Transactional
    public TicketResponse assign(Long id, String assignee, String operator) {
        SupportTicket t = requireEntity(id);
        expectStatus(t, TicketStatus.OPEN, "指派");
        t.setStatus(TicketStatus.ASSIGNED);
        t.setAssignee(assignee);
        SupportTicket saved = repository.save(t);
        audit.record("ASSIGN", "Ticket", String.valueOf(id),
                "指派工单给 " + assignee, operator);
        return TicketResponse.from(saved);
    }

    /** ASSIGNED → PROCESSING：开始处理。 */
    @Transactional
    public TicketResponse startProcessing(Long id, String operator) {
        SupportTicket t = requireEntity(id);
        expectStatus(t, TicketStatus.ASSIGNED, "开始处理");
        t.setStatus(TicketStatus.PROCESSING);
        SupportTicket saved = repository.save(t);
        audit.record("PROCESS", "Ticket", String.valueOf(id),
                "开始处理工单", operator);
        return TicketResponse.from(saved);
    }

    /** PROCESSING → RESOLVED：填写解决方案。 */
    @Transactional
    public TicketResponse resolve(Long id, String resolution, String operator) {
        SupportTicket t = requireEntity(id);
        expectStatus(t, TicketStatus.PROCESSING, "解决");
        t.setStatus(TicketStatus.RESOLVED);
        t.setResolution(resolution);
        SupportTicket saved = repository.save(t);
        audit.record("RESOLVE", "Ticket", String.valueOf(id),
                "解决工单：" + resolution, operator);
        return TicketResponse.from(saved);
    }

    /** RESOLVED → CLOSED：关闭工单。 */
    @Transactional
    public TicketResponse close(Long id, String operator) {
        SupportTicket t = requireEntity(id);
        expectStatus(t, TicketStatus.RESOLVED, "关闭");
        t.setStatus(TicketStatus.CLOSED);
        t.setClosedAt(Instant.now());
        SupportTicket saved = repository.save(t);
        audit.record("CLOSE", "Ticket", String.valueOf(id),
                "关闭工单", operator);
        return TicketResponse.from(saved);
    }

    public Page<TicketResponse> list(TicketStatus status, TicketCategory category,
                                     int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pr = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "id"));
        if (status != null && category != null) {
            return repository.findByStatusAndCategory(status, category, pr).map(TicketResponse::from);
        }
        if (status != null) {
            return repository.findByStatus(status, pr).map(TicketResponse::from);
        }
        if (category != null) {
            return repository.findByCategory(category, pr).map(TicketResponse::from);
        }
        return repository.findAll(pr).map(TicketResponse::from);
    }

    public TicketResponse get(Long id) {
        return TicketResponse.from(requireEntity(id));
    }

    public List<TicketResponse> findByPlayer(String playerUsername) {
        return repository.findByPlayerUsername(playerUsername).stream()
                .map(TicketResponse::from).toList();
    }

    // ---------- 内部方法 ----------

    private SupportTicket requireEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Ticket", id));
    }

    /** 校验当前状态是否为期望值，不是则 409。 */
    private void expectStatus(SupportTicket t, TicketStatus expected, String action) {
        if (t.getStatus() != expected) {
            throw new ConflictException(
                    "工单 id=" + t.getId() + " 当前状态 " + t.getStatus() + "，不可执行「" + action + "」");
        }
    }
}
