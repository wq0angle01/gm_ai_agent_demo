package com.gm.demo.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 基于 MySQL 的会话记忆持久化实现。
 *
 * <p>替换默认的 {@code InMemoryChatMemoryRepository}，让对话历史跨进程重启保留。
 * 表结构见 seed.sql 里的 {@code chat_memory}（id 自增即时间线顺序）。
 *
 * <p>设计选择：用 JdbcTemplate 而不是 MyBatis-Plus——
 * 这是 Spring AI SPI 接口的适配层（把 Message 对象映射到行），不是业务实体，
 * 没有 Service 复用需求，也没有分页/Wrapper 的场景，JdbcTemplate 最直接。
 *
 * <p>saveAll 的语义是"全量替换"（MessageWindowChatMemory 先读后合并再写回），
 * 实现里先 DELETE 再批量 INSERT，与 InMemory 版 put 行为等价。
 */
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbc;

    public JdbcChatMemoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> findConversationIds() {
        return jdbc.queryForList("SELECT DISTINCT conversation_id FROM chat_memory", String.class);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbc.query(
                "SELECT message_type, content FROM chat_memory WHERE conversation_id = ? ORDER BY id",
                (rs, i) -> toMessage(rs),
                conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 全量替换：先清后写，语义等价于 InMemory 版 map.put(key, list)
        jdbc.update("DELETE FROM chat_memory WHERE conversation_id = ?", conversationId);
        if (messages.isEmpty()) {
            return;
        }
        // 批量插入——比逐条 update 少 N 次网络往返
        List<Object[]> batch = messages.stream()
                .map(m -> new Object[]{conversationId, m.getMessageType().name(), m.getText()})
                .toList();
        jdbc.batchUpdate(
                "INSERT INTO chat_memory (conversation_id, message_type, content) VALUES (?, ?, ?)",
                batch);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbc.update("DELETE FROM chat_memory WHERE conversation_id = ?", conversationId);
    }

    /**
     * 从结果集重建 Spring AI Message 对象。
     * 只处理 USER/ASSISTANT/SYSTEM——MessageChatMemoryAdvisor 实际只存这三种；
     * TOOL 类型理论上不出现（工具中间态由框架在每次请求内重建，不入记忆），
     * 兜底当 UserMessage 处理避免 NPE。
     */
    private static Message toMessage(ResultSet rs) throws SQLException {
        String type = rs.getString("message_type");
        String content = rs.getString("content");
        if (content == null) {
            content = "";
        }
        if (MessageType.USER.name().equals(type)) {
            return new UserMessage(content);
        } else if (MessageType.ASSISTANT.name().equals(type)) {
            return new AssistantMessage(content);
        } else if (MessageType.SYSTEM.name().equals(type)) {
            return new SystemMessage(content);
        }
        // TOOL 或未知类型：保守处理，包成 UserMessage 让链路不崩
        return new UserMessage("[tool-history] " + content);
    }
}
