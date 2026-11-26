package com.gm.demo.entity.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 问一句的请求体。
 *
 * @param question       要问模型的原话；上限是为了挡住误传的超长文本，它直接换算成 token 费用
 * @param requestId      幂等键，可选。客户端为"同一笔意图"复用的稳定 ID；缺省则服务端按单次请求生成，
 *                       只能挡住模型在这一轮里对同一写操作的手抖重复，跳请求幂等需客户端显式传同一个值
 * @param conversationId 会话标识，可选。同一会话的多轮问答共享这个 ID，模型能看到历史；
 *                       缺省则服务端按单次请求生成，等同于无记忆的单轮问答
 */
public record AskRequest(

        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题最长 500 个字符")
        String question,

        @Size(max = 64, message = "requestId 最长 64 个字符")
        String requestId,

        @Size(max = 64, message = "conversationId 最长 64 个字符")
        String conversationId) {

    /** 兼容只传问题的老调用（含测试）：requestId 和 conversationId 走服务端生成。 */
    public AskRequest(String question) {
        this(question, null, null);
    }
}
