DROP TABLE IF EXISTS `chat_record`;
CREATE TABLE `chat_record`
(
    `id`           bigint                                  NOT NULL COMMENT '主键ID',
    `session_id`   varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
    `session_type` tinyint                                 NOT NULL DEFAULT 0 COMMENT '会话类型',
    `user_id`      bigint                                  NOT NULL COMMENT '用户ID',
    `title`        varchar(255) COLLATE utf8mb4_unicode_ci          DEFAULT '新对话' COMMENT '聊天标题',
    `create_time`  datetime                                NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  datetime                                NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `session_id` (`session_id`),
    UNIQUE KEY `idx_session_id` (`session_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='聊天记录表'
