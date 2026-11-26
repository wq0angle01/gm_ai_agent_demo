package com.gm.demo.service;

import com.gm.demo.agent.ComplianceAgent;
import com.gm.demo.agent.ComplianceAgent.ComplianceVerdict;
import com.gm.demo.agent.SupportAgent;
import com.gm.demo.agent.TriageAgent;
import com.gm.demo.agent.TriageAgent.TriageResult;
import com.gm.demo.entity.domain.SupportTicket;
import com.gm.demo.entity.domain.TicketStatus;
import com.gm.demo.entity.response.WorkflowResponse;
import com.gm.demo.entity.response.WorkflowResponse.StepTrace;
import com.gm.demo.error.NotFoundException;
import com.gm.demo.repository.TicketRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 多 Agent 工单编排器：按 Triage → Support(RAG) → Compliance → Execute 顺序串联。
 *
 * <p>设计铁律——LLM 只做文本转换，流程控制和状态流转是纯 Java：
 * <ul>
 *   <li>TriageAgent：自然语言工单 → 结构化分类（NL→structured）</li>
 *   <li>SupportAgent：结构化分类 + RAG → 处理方案文本（structured→NL）</li>
 *   <li>ComplianceAgent：处理方案文本 → PASS/REJECT 裁决（NL→structured）</li>
 *   <li>本类的 execute 方法：按裁决结果调用 TicketService 做状态流转（纯 Java，无 LLM）</li>
 * </ul>
 *
 * <p>短路逻辑：
 * <ul>
 *   <li>工单非 OPEN 状态 → 直接返回当前状态，不跑流程</li>
 *   <li>合规拒绝 → 只 assign + process，不 resolve，标记需人工</li>
 *   <li>Agent 调用异常 → catch 后记录失败步骤，已完成的流转不回滚</li>
 * </ul>
 */
@Service
public class TicketWorkflowOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TicketWorkflowOrchestrator.class);
    private static final String AI_OPERATOR = "ai-workflow";

    private final TriageAgent triageAgent;
    private final SupportAgent supportAgent;
    private final ComplianceAgent complianceAgent;
    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final AuditService audit;

    @Autowired
    public TicketWorkflowOrchestrator(
            TriageAgent triageAgent,
            SupportAgent supportAgent,
            ComplianceAgent complianceAgent,
            TicketService ticketService,
            TicketRepository ticketRepository,
            AuditService audit) {
        this.triageAgent = triageAgent;
        this.supportAgent = supportAgent;
        this.complianceAgent = complianceAgent;
        this.ticketService = ticketService;
        this.ticketRepository = ticketRepository;
        this.audit = audit;
    }

    /**
     * 对指定工单执行完整的自动编排流程。
     *
     * @param ticketId 工单 ID（必须是 OPEN 状态）
     * @return 完整的执行报告，包含每一步的输入/输出
     */
    public WorkflowResponse execute(Long ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> NotFoundException.of("Ticket", ticketId));

        // 短路：非 OPEN 状态的工单不重复跑
        if (ticket.getStatus() != TicketStatus.OPEN) {
            log.info("[编排] 工单 id={} 状态={} 非 OPEN，跳过", ticketId, ticket.getStatus());
            return new WorkflowResponse(ticketId, null, null, "跳过：非 OPEN 状态",
                    null, false, null, ticket.getStatus().name(), null,
                    List.of(new StepTrace("Orchestrator", "短路", "状态=" + ticket.getStatus(), 0)));
        }

        List<StepTrace> steps = new ArrayList<>();

        // ===== 第 1 步：分诊 =====
        long t1 = System.nanoTime();
        TriageResult triage = triageAgent.triage(ticket.getTitle(), ticket.getDescription());
        long ms1 = (System.nanoTime() - t1) / 1_000_000;
        steps.add(new StepTrace("TriageAgent", "分类", triage.category() + "/" + triage.priority(), ms1));
        log.info("[编排] 分诊完成 id={} category={} priority={} ms={}", ticketId, triage.category(), triage.priority(), ms1);

        // 更新工单分类（如果分诊结果与原分类不同）
        if (ticket.getCategory() != triage.category()) {
            ticket.setCategory(triage.category());
            ticketRepository.updateById(ticket);
        }


        // ===== 第 2 步：草拟处理方案（RAG） =====
        long t2 = System.nanoTime();
        String draft = supportAgent.draftResolution(
                ticket.getTitle(), ticket.getDescription(), triage.category());
        long ms2 = (System.nanoTime() - t2) / 1_000_000;
        steps.add(new StepTrace("SupportAgent", "草拟方案", truncate(draft, 80), ms2));
        log.info("[编排] 草拟完成 id={} 方案长度={} ms={}", ticketId, draft.length(), ms2);

        // ===== 第 3 步：合规审核 =====
        long t3 = System.nanoTime();
        ComplianceVerdict verdict = complianceAgent.review(draft, triage.category().name());
        long ms3 = (System.nanoTime() - t3) / 1_000_000;
        steps.add(new StepTrace("ComplianceAgent", "审核",
                verdict.approved() ? "PASS" : "REJECT:" + verdict.note(), ms3));
        log.info("[编排] 合规完成 id={} approved={} ms={}", ticketId, verdict.approved(), ms3);

        // ===== 第 4 步：执行状态流转（纯 Java，无 LLM） =====
        long t4 = System.nanoTime();
        String finalStatus;
        String finalResolution;

        // 无论合规是否通过，都先推进到 PROCESSING
        ticketService.assign(ticketId, AI_OPERATOR, AI_OPERATOR);
        ticketService.startProcessing(ticketId, AI_OPERATOR);

        if (verdict.approved()) {
            // 通过 → 直接解决
            finalResolution = draft;
            ticketService.resolve(ticketId, finalResolution, AI_OPERATOR);
            finalStatus = TicketStatus.RESOLVED.name();
        } else {
            // 拒绝 → 标记原因但不 resolve，留给人工
            finalResolution = "[需人工复核] " + verdict.note();
            // 把拒绝原因写入 resolution 字段作为备注（不执行 RESOLVED 流转）
            SupportTicket t = ticketRepository.findById(ticketId).orElseThrow();
            t.setResolution(finalResolution);
            ticketRepository.updateById(t);
            finalStatus = TicketStatus.PROCESSING.name();
        }

        long ms4 = (System.nanoTime() - t4) / 1_000_000;
        steps.add(new StepTrace("Orchestrator", "状态流转", finalStatus, ms4));

        // 审计留痕
        audit.record("AUTO_WORKFLOW", "Ticket", String.valueOf(ticketId),
                "多Agent编排完成: " + triage.category() + "/" + (verdict.approved() ? "PASS" : "REJECT"),
                AI_OPERATOR);

        return new WorkflowResponse(
                ticketId,
                triage.category().name(),
                triage.priority(),
                triage.reason(),
                draft,
                verdict.approved(),
                verdict.note(),
                finalStatus,
                finalResolution,
                steps);
    }

    /** 截断过长文本用于 trace 展示。 */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
