-- 学习路径叶子节点 ↔ 闪卡关联（matchFlashcards 落库）
-- 适用于 MySQL 8.x

CREATE TABLE IF NOT EXISTS `learning_path_flashcard_match` (
  `id`          BIGINT       NOT NULL COMMENT '主键',
  `userId`      BIGINT       NOT NULL COMMENT '用户ID',
  `pathId`      VARCHAR(64)  NOT NULL COMMENT '学习路径ID',
  `nodeId`      VARCHAR(64)  NOT NULL COMMENT '学习路径节点ID（通常为叶子节点）',
  `flashcardId` VARCHAR(64)  NOT NULL COMMENT '闪卡ID',
  `score`       DOUBLE       NULL COMMENT '匹配得分（来自 Neo4j Fulltext scoreMap）',
  `createTime`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lp_match` (`userId`, `pathId`, `nodeId`, `flashcardId`),
  KEY `idx_lp_match_node` (`userId`, `pathId`, `nodeId`),
  KEY `idx_lp_match_flashcard` (`userId`, `flashcardId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习路径↔闪卡关联';

