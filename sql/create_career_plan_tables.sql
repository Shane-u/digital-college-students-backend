-- 职业规划模块：报告历史 + 职业测评历史
-- 说明：
-- 1) 字段风格与项目现有表一致：userId / createTime / updateTime / isDelete
-- 2) MyBatis-Plus 全局逻辑删除字段：isDelete
-- 3) id 使用 BIGINT（适配 MyBatis-Plus ASSIGN_ID）

-- 职业规划报告历史（Dify 工作流输出的最终报告落库）
CREATE TABLE IF NOT EXISTS `career_plan_report` (
  `id`            BIGINT       NOT NULL COMMENT '主键 ID',
  `userId`        BIGINT       NOT NULL COMMENT '用户 ID',
  `runId`         VARCHAR(64)  NOT NULL COMMENT '工作流运行 ID（UUID）',
  `reportMarkdown` LONGTEXT             DEFAULT NULL COMMENT '职业规划报告 Markdown（最终产物）',
  `error`         LONGTEXT              DEFAULT NULL COMMENT '失败原因',
  `createTime`    DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`    DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`      TINYINT               DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_cpr_userId` (`userId`),
  KEY `idx_cpr_runId` (`runId`),
  KEY `idx_cpr_createTime` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='职业规划报告历史（Dify 工作流结果）';

-- 职业测评历史（心理测评/职业测评结果落库，用户可复用历史结果）
CREATE TABLE IF NOT EXISTS `career_assessment_history` (
  `id`            BIGINT       NOT NULL COMMENT '主键 ID',
  `userId`        BIGINT       NOT NULL COMMENT '用户 ID',
  `assessmentType` VARCHAR(64) NOT NULL COMMENT '测评类型',
  `source`        VARCHAR(64)           DEFAULT NULL COMMENT '来源（如：web/app/import）',
  `assessmentJson` LONGTEXT     NOT NULL COMMENT '测评结果 JSON（字符串）',
  `createTime`    DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`    DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`      TINYINT               DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_cah_userId` (`userId`),
  KEY `idx_cah_userId_type` (`userId`, `assessmentType`),
  KEY `idx_cah_createTime` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='职业测评历史';

ALTER TABLE `career_plan_report`
    DROP COLUMN `status`,
    DROP COLUMN `userInput`,
    DROP COLUMN `assessmentJson`,
    DROP COLUMN `linksJson`;
