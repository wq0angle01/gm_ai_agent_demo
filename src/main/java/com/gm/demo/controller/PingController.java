package com.gm.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 存活探针，不访问数据库；返回 profile 和端口以便确认跑的是哪个配置。 */
@RestController
@RequestMapping("/api")
@Tag(name = "00-运维", description = "存活探针")
public class PingController {

    private final Environment env;


    @Autowired
    public PingController(Environment env) {
        this.env = env;
    }

    public record PingResponse(boolean pong, Instant serverTime, String app, String[] profiles, String port) {}

    @Operation(summary = "存活探针")
    @GetMapping("/ping")
    public PingResponse ping() {
        return new PingResponse(
                true,
                Instant.now(),
                env.getProperty("spring.application.name", "unknown"),
                env.getActiveProfiles(),
                env.getProperty("server.port", "8080"));
    }
}
