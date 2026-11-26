package com.gm.demo.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 把异常翻译成状态码 + 统一错误体，业务代码因此不需要自己 try/catch。
 *
 * <p>日志级别约定：4xx 是可预期结果，打 INFO/WARN；只有 5xx 打 ERROR 带堆栈，
 * 否则接上告警后无效噪音会盖掉真实故障。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        log.info("[404] {} <- {}", ex.getMessage(), req.getRequestURI());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    /** Unknown path: Spring 6.1+ raises this, and without a dedicated handler the Exception fallback would turn it into a 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        log.info("[404] no endpoint {} <- {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "no such endpoint: " + req.getMethod() + " " + req.getRequestURI(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex, HttpServletRequest req) {
        log.info("[409] {} <- {}", ex.getMessage(), req.getRequestURI());
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    /** @Valid 未通过，逐字段返回失败原因供表单标红。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest req) {
        List<ApiErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        log.info("[400] 校验失败 {} 项 <- {}", fields.size(), req.getRequestURI());
        return ResponseEntity.badRequest().body(ApiErrorResponse.validation(
                "request validation failed", req.getRequestURI(), newTraceId(), fields));
    }

    /**
     * 唯一约束兜底：Service 的预检挡不住并发，最终由数据库拦下，翻译为 409 而不是 500。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException ex,
                                                            HttpServletRequest req) {
        log.warn("[409] 约束冲突 <- {} {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "data constraint violated, the resource already exists", req);
    }

    /** 兜底：堆栈只进日志，响应里只给 traceId，避免泄露内部信息。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        String traceId = newTraceId();
        log.error("[500] 未预期异常 traceId={} uri={}", traceId, req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                500, "Internal Server Error",
                "internal error, report this traceId: " + traceId, req.getRequestURI(), traceId));
    }

    private ApiErrorResponse.FieldError toFieldError(FieldError fe) {
        return new ApiErrorResponse.FieldError(
                fe.getField(), String.valueOf(fe.getRejectedValue()), fe.getDefaultMessage());
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
                status.value(), status.getReasonPhrase(), message, req.getRequestURI(), newTraceId()));
    }

    /** 生产环境应替换为网关注入的 traceId（MDC / OpenTelemetry）。 */
    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
