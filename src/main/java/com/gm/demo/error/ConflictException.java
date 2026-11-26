package com.gm.demo.error;

/**
 * 与已有资源冲突（典型场景：用户名重复），映射到 409。
 *
 * <p>不用 400：请求本身合法，只是与服务器当前状态冲突；不用 500：这是可预期的业务结果。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
