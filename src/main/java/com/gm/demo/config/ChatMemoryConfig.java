package com.gm.demo.config;

import com.gm.demo.repository.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 用 MySQL 持久化会话记忆，替换 spring-ai-autoconfigure-model-chat-memory 默认的内存实现。
 *
 * <p>生效原理：ChatMemoryAutoConfiguration 的 chatMemoryRepository() 标注了 @ConditionalOnMissingBean，
 * 本配置先注册一个 ChatMemoryRepository bean，自动配置检测到已有就退让。
 * MessageWindowChatMemory（ChatMemory 实现）仍由自动配置创建，只是内部注入的 repository 换成了我们的 JDBC 版。
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemoryRepository chatMemoryRepository(@Autowired JdbcTemplate jdbc) {
        return new JdbcChatMemoryRepository(jdbc);
    }
}
