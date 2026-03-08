-- 孪孪伴学 - 学习路径表
-- 存储用户的学习路径元数据及 JSON 格式的图谱信息

CREATE TABLE IF NOT EXISTS learning_path
(
    id          VARCHAR(64)                       NOT NULL COMMENT '学习路径ID' PRIMARY KEY,
    userId      BIGINT                            NOT NULL COMMENT '用户ID',
    topic       VARCHAR(256)                      NOT NULL COMMENT '路径主题',
    description VARCHAR(512)                     NULL COMMENT '路径描述',
    pathJson    LONGTEXT                          NULL COMMENT 'JSON格式的学习路径图谱（节点列表及关系）',
    createTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete    TINYINT  DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_userId (userId),
    INDEX idx_topic (topic(128)),
    INDEX idx_createTime (createTime)
) COMMENT '孪孪伴学-学习路径表' COLLATE = utf8mb4_unicode_ci;
