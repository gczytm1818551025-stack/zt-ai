DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`
(
    `id`          bigint NOT NULL COMMENT '主键ID',
    `nick_name`   varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
    `phone`       varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
    `create_time` datetime                               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime                               DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`) COMMENT '手机号唯一索引'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户表'
