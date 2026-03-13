-- 闪卡测试 & 成长轨迹 扩展建表脚本
-- 适用于 MySQL 8.x

-- =========================================
-- 1）记忆闪卡主表（如已存在，请对照调整，无需重复执行）
-- =========================================
CREATE TABLE IF NOT EXISTS `flash_card` (
  `id`              BIGINT       NOT NULL COMMENT '闪卡ID（MP ASSIGN_ID，雪花ID）',
  `userId`          BIGINT       NOT NULL COMMENT '用户ID',
  `title`           VARCHAR(255) NOT NULL COMMENT '知识点标题',
  `content`         TEXT         NULL COMMENT '详细知识点内容（纯文本）',
  `htmlContent`     LONGTEXT     NULL COMMENT '闪卡HTML内容（HTML+CSS+SVG）',
  `originalContent` LONGTEXT     NULL COMMENT '原始AI回答内容',
  `nextReviewTime`  DATETIME     NULL COMMENT '下次复习时间（SM-2算法）',
  `repetition`      INT          NULL COMMENT '复习次数',
  `lastReviewTime`  DATETIME     NULL COMMENT '最后复习时间',
  `ef`              DOUBLE       NULL COMMENT '易忘系数(EF)，默认2.5',
  `interval`        INT          NULL COMMENT '复习间隔（天），实体中用@TableField(\"`interval`\")',
  `difficultyLevel` INT          NULL COMMENT '难度等级（1-重来，2-困难，3-良好，4-简单）',
  `hierarchyPath`   VARCHAR(512) NULL COMMENT '层级路径，如 根/课程/HTML',
  `createTime`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_flash_card_user` (`userId`),
  KEY `idx_flash_card_nextReviewTime` (`nextReviewTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆闪卡';


-- =========================================
-- 2）闪卡测试主表 flashcard_test
-- =========================================
CREATE TABLE IF NOT EXISTS `flashcard_test` (
  `id`          BIGINT       NOT NULL COMMENT '主键',
  `userId`      BIGINT       NOT NULL COMMENT '用户ID',
  `pathId`      BIGINT       NULL COMMENT '学习路径ID，可为空',
  `nodeId`      VARCHAR(64)  NOT NULL COMMENT '闪卡/学习节点ID',
  `difficulty`  VARCHAR(16)  NOT NULL COMMENT '难度：easy/medium/hard',
  `score`       INT          NULL COMMENT '总分',
  `testTime`    DATETIME     NULL COMMENT '测试时间',
  `aiAdvice`    TEXT         NULL COMMENT 'AI学习建议',
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'init' COMMENT '状态：init/finished/cancelled等',
  `createTime`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_flashcard_test_user` (`userId`),
  KEY `idx_flashcard_test_node` (`nodeId`),
  KEY `idx_flashcard_test_time` (`testTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闪卡测试主记录';


-- =========================================
-- 3）闪卡测试题目表 flashcard_test_question
-- =========================================
CREATE TABLE IF NOT EXISTS `flashcard_test_question` (
  `id`            BIGINT       NOT NULL COMMENT '主键',
  `testId`        BIGINT       NOT NULL COMMENT '所属测试ID',
  `questionType`  VARCHAR(16)  NOT NULL COMMENT '题目类型：choice/blank/code',
  `content`       TEXT         NOT NULL COMMENT '题干',
  `options`       TEXT         NULL COMMENT '选择题选项(JSON数组字符串)',
  `answer`        TEXT         NULL COMMENT '标准答案/参考代码',
  `aiAnswer`      TEXT         NULL COMMENT 'AI批改解析或评语',
  `userAnswer`    TEXT         NULL COMMENT '用户作答内容',
  `userUploadUrl` VARCHAR(512) NULL COMMENT '用户上传图片URL（编程题拍照）',
  `score`         INT          NOT NULL DEFAULT 0 COMMENT '本题分值',
  `userScore`     INT          NULL COMMENT '本题得分（批改后写入）',
  `createTime`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_flashcard_test_question_testId` (`testId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闪卡测试题目记录';


-- =========================================
-- 3.1）闪卡测试提交历史表 flashcard_test_attempt（一次 submit 一条记录，可追溯）
-- =========================================
CREATE TABLE IF NOT EXISTS `flashcard_test_attempt` (
  `id`                 BIGINT       NOT NULL COMMENT '主键',
  `userId`             BIGINT       NOT NULL COMMENT '用户ID',
  `testId`             BIGINT       NOT NULL COMMENT '所属测试ID（试卷）',
  `totalScore`         INT          NULL COMMENT '本次提交总分',
  `pass`               TINYINT(1)   NULL COMMENT '是否通过（>=60）',
  `aiAdvice`           TEXT         NULL COMMENT 'AI学习建议（本次提交）',
  `questionResultsJson` LONGTEXT    NULL COMMENT '逐题批改明细快照(JSON)',
  `createTime`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_flashcard_test_attempt_user` (`userId`),
  KEY `idx_flashcard_test_attempt_test` (`testId`),
  KEY `idx_flashcard_test_attempt_time` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闪卡测试提交历史';

ALTER TABLE `flashcard_test_question`
  ADD COLUMN `userScore` INT NULL COMMENT '本题得分（批改后写入）' AFTER `score`;


-- =========================================
-- 4）growth_record 表扩展测试相关字段
--    如已添加过这些列，可按需注释掉对应 ALTER
-- =========================================
ALTER TABLE `growth_record`
  ADD COLUMN `testId`     BIGINT       NULL COMMENT '关联闪卡测试ID' AFTER `isDelete`;

ALTER TABLE `growth_record`
  ADD COLUMN `nodeId`     VARCHAR(64)  NULL COMMENT '关联学习节点ID（如闪卡ID）' AFTER `testId`;

ALTER TABLE `growth_record`
  ADD COLUMN `score`      INT          NULL COMMENT '本次测试得分' AFTER `nodeId`;

ALTER TABLE `growth_record`
  ADD COLUMN `litStatus`  TINYINT(1)   NULL COMMENT '点亮状态(0-未点亮,1-已点亮)' AFTER `score`;

ALTER TABLE `growth_record`
  ADD COLUMN `litProgress` INT         NULL COMMENT '点亮进度(0-100，预留)' AFTER `litStatus`;

-- 索引：如不支持 IF NOT EXISTS，请先 SHOW INDEX 再创建
CREATE INDEX idx_growth_record_testId ON `growth_record` (`testId`);
CREATE INDEX idx_growth_record_nodeId ON `growth_record` (`nodeId`);

