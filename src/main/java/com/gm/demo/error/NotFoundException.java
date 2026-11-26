package com.gm.demo.error;

/**
 * 资源不存在，由全局处理器翻译成 404。
 *
 * <p>用异常而不是返回 null 或提示文案：调用方只需处理成功路径，失败在 HTTP 层可见。
 * <p>继承 RuntimeException：各层无需声明 throws，且 Spring 事务默认只对它回滚。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " not found: id=" + id);
    }
}
