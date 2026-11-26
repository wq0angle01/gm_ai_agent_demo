-- 演示数据：每次启动由 spring.sql.init 执行，开关和编码在 application.properties 里。
-- 写成 INSERT ... SELECT ... WHERE NOT EXISTS，重复执行就是空操作，而不是撞唯一键报错。
-- 不要用 INSERT IGNORE：它会把真正的错误一起吞掉。
-- 列名就是 Hibernate 建出来的那些，id 是自增，交给数据库。
-- 时间列按 UTC 填：实测驱动读写 datetime 都不做时区换算，Instant.now() 落库就是 UTC 墙钟，
-- 转东八区显示是读取之后的事（见 PlayerQueryTool），所以这里写本地时间会整体偏 8 小时。

-- === AI 会话记忆持久化表 ===
-- 历史由 Hibernate 建的表不含这张，它属于新增的 AI 基础设施，用 CREATE IF NOT EXISTS 幂等建。
-- 设计意图：每行存一条消息，按 conversation_id 分组，按 id 自增顺序即时间线。
-- message_type 对应 Spring AI MessageType 枚举（USER/ASSISTANT/SYSTEM/TOOL）。
CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO player (username, nickname, level, vip, gold, diamond, recharge_amount, status, created_at, last_login_at)
SELECT 'hero_001', '测试玩家一号', 1, 0, 1000, 50, 128.00, 'NORMAL', '2026-11-01 02:00:00', '2026-11-20 13:05:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM player WHERE username = 'hero_001');

INSERT INTO player (username, nickname, level, vip, gold, diamond, recharge_amount, status, created_at, last_login_at)
SELECT 'whale_002', '大R玩家二哥', 1, 0, 88, 99999, 64800.00, 'NORMAL', '2026-11-05 06:20:00', '2026-11-21 00:40:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM player WHERE username = 'whale_002');

INSERT INTO player (username, nickname, level, vip, gold, diamond, recharge_amount, status, created_at, last_login_at)
SELECT 'newbie_003', '萌新三弟', 1, 0, 0, 0, 0.00, 'NORMAL', '2026-11-18 11:00:00', NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM player WHERE username = 'newbie_003');

-- 邮件演示数据
INSERT INTO mail (subject, content, gold, diamond, target_type, target_player_ids, status, sender, created_at, sent_at)
SELECT '维护补偿', '服务器维护完成，赠送1000金币和50钻石作为补偿。', 1000, 50, 'ALL', NULL, 'SENT', 'admin', '2026-11-20 02:00:00', '2026-11-20 02:05:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM mail WHERE subject = '维护补偿');

INSERT INTO mail (subject, content, gold, diamond, target_type, target_player_ids, status, sender, created_at, sent_at)
SELECT '内测反馈奖励', '感谢参与内测反馈，奖励200钻石。', 0, 200, 'SPECIFIED', '9,10', 'DRAFT', 'admin', '2026-11-21 10:00:00', NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM mail WHERE subject = '内测反馈奖励');

-- 公告演示数据
INSERT INTO notice (title, content, priority, pinned, status, effective_at, expire_at, publisher, created_at, published_at)
SELECT '版本更新 v1.2', '新增竞技场玩法，修复已知问题。', 5, false, 'PUBLISHED', '2026-11-20 03:00:00', NULL, 'admin', '2026-11-20 02:30:00', '2026-11-20 03:00:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM notice WHERE title = '版本更新 v1.2');

INSERT INTO notice (title, content, priority, pinned, status, effective_at, expire_at, publisher, created_at, published_at)
SELECT '停机维护通知', '计划于 12 月 1 日 02:00-06:00 停机维护。', 100, true, 'DRAFT', '2026-12-01 02:00:00', '2026-12-01 06:00:00', 'admin', '2026-11-22 10:00:00', NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM notice WHERE title = '停机维护通知');

-- 道具目录演示数据
INSERT INTO game_item (code, name, type, description, max_stack)
SELECT 'POTION_HP', '小血瓶', 'CONSUMABLE', '恢复 100 HP', 99
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM game_item WHERE code = 'POTION_HP');

INSERT INTO game_item (code, name, type, description, max_stack)
SELECT 'SWORD_IRON', '铁剑', 'EQUIPMENT', '攻击力 +15', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM game_item WHERE code = 'SWORD_IRON');

INSERT INTO game_item (code, name, type, description, max_stack)
SELECT 'GOLD_POUCH', '金币袋', 'CURRENCY', '打开获得 500 金币', 999
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM game_item WHERE code = 'GOLD_POUCH');

-- 工单演示数据
INSERT INTO support_ticket (title, description, player_username, category, status, assignee, resolution, created_at, updated_at, closed_at)
SELECT '充值未到账', '6元礼包购买后钻石未增加，订单号 TXN_20261120_001。', 'hero_001', 'PAYMENT', 'OPEN', NULL, NULL, '2026-11-20 10:00:00', '2026-11-20 10:00:00', NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM support_ticket WHERE title = '充值未到账');

INSERT INTO support_ticket (title, description, player_username, category, status, assignee, resolution, created_at, updated_at, closed_at)
SELECT '举报作弊', '玩家 newbie_003 在竞技场使用加速外挂。', 'whale_002', 'CHEAT', 'PROCESSING', 'cs_agent_01', NULL, '2026-11-21 08:30:00', '2026-11-21 09:00:00', NULL
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM support_ticket WHERE title = '举报作弊');

INSERT INTO support_ticket (title, description, player_username, category, status, assignee, resolution, created_at, updated_at, closed_at)
SELECT '建议增加公会战', '希望在跨服玩法中加入公会团体对抗模式。', 'hero_001', 'SUGGESTION', 'CLOSED', 'cs_agent_02', '已转交策划组，纳入 Q1 规划。', '2026-11-18 15:00:00', '2026-11-19 10:00:00', '2026-11-19 10:00:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM support_ticket WHERE title = '建议增加公会战');
