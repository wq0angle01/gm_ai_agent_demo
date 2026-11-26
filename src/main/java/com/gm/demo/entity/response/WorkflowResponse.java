package com.gm.demo.entity.response;

import java.util.List;

/**
 * 多 Agent 工单编排的完整执行报告。
 * 每一步都记录输入/输出，方便前端展示流水日志和人工回溯决策依据。
 */
public record WorkflowResponse(
        Long ticketId,
        String triageCategory,
        String triagePriority,
        String triageReason,
        String supportDraft,
        boolean complianceApproved,
        String complianceNote,
        String finalStatus,
        String finalResolution,
        List<StepTrace> steps) {

    /** 单步执行轨迹，用于透明化展示。 */
    public record StepTrace(String agent, String action, String output, long tookMs) {}
}
