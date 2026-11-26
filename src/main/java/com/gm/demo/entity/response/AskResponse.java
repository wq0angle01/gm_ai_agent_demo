package com.gm.demo.entity.response;

/**
 * 问一句的响应体。
 *
 * @param question 原样回显，方便前端把问答配在一起显示
 * @param answer   模型返回的文本
 * @param model    实际调用的模型名，来自配置
 * @param tookMs   这次模型往返耗时，毫秒
 */
public record AskResponse(String question, String answer, String model, long tookMs) {
}
