package com.gm.demo.error;

import java.time.Instant;
import java.util.List;

/** 统一的失败响应体；成功响应不套这层壳。 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId,
        List<FieldError> fieldErrors) {

    /** 字段级校验错误，field 为请求体属性名。 */
    public record FieldError(String field, String rejectedValue, String message) {}

    public static ApiErrorResponse of(int status, String error, String message, String path, String traceId) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path, traceId, List.of());
    }

    public static ApiErrorResponse validation(String message, String path, String traceId, List<FieldError> fields) {
        return new ApiErrorResponse(Instant.now(), 400, "Bad Request", message, path, traceId, fields);
    }
}
