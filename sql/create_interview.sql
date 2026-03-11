-- AI 面试相关表结构初始化脚本
-- 候选人原始简历与解析结果
CREATE TABLE IF NOT EXISTS `ai_candidate_resume` (
  `id`                BIGINT       NOT NULL COMMENT '主键 ID',
  `userId`            BIGINT       NOT NULL COMMENT '关联用户 ID',
  `fileUrl`           VARCHAR(512) NOT NULL COMMENT '原始上传文件在对象存储或本地的访问地址',
  `originalFilename`  VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `fileType`          VARCHAR(100)          DEFAULT NULL COMMENT '文件 MIME 类型',
  `rawText`           LONGTEXT              DEFAULT NULL COMMENT '解析出的纯文本内容',
  `parsedJson`        LONGTEXT              DEFAULT NULL COMMENT '结构化解析 JSON（如基础信息、教育、经历等）',
  `createTime`        DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`        DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`          TINYINT               DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_acr_userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 面试候选人原始简历与解析结果';

-- 候选人结构化画像
CREATE TABLE IF NOT EXISTS `ai_candidate_profile` (
  `id`                  BIGINT       NOT NULL COMMENT '主键 ID',
  `resumeId`            BIGINT       NOT NULL COMMENT '对应的简历 ID',
  `userId`              BIGINT       NOT NULL COMMENT '关联用户 ID',
  `name`                VARCHAR(100)         DEFAULT NULL COMMENT '姓名',
  `email`               VARCHAR(255)         DEFAULT NULL COMMENT '邮箱',
  `phone`               VARCHAR(50)          DEFAULT NULL COMMENT '手机号',
  `yearsOfExperience`   DOUBLE               DEFAULT NULL COMMENT '工作年限（年）',
  `highestDegree`       VARCHAR(100)         DEFAULT NULL COMMENT '最高学历',
  `school`              VARCHAR(255)         DEFAULT NULL COMMENT '学校',
  `major`               VARCHAR(255)         DEFAULT NULL COMMENT '专业',
  `skillsJson`          LONGTEXT             DEFAULT NULL COMMENT '技能列表 JSON',
  `projectsJson`        LONGTEXT             DEFAULT NULL COMMENT '项目经历 JSON',
  `workExperiencesJson` LONGTEXT             DEFAULT NULL COMMENT '工作经历 JSON',
  `extraJson`           LONGTEXT             DEFAULT NULL COMMENT '其他额外字段 JSON',
  `createTime`          DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`          DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`            TINYINT              DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_acp_userId` (`userId`),
  KEY `idx_acp_resumeId` (`resumeId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 面试候选人简历的结构化画像';

-- 简历分析
CREATE TABLE IF NOT EXISTS `ai_resume_analysis` (
  `id`           BIGINT       NOT NULL COMMENT '主键 ID',
  `resumeId`     BIGINT       NOT NULL COMMENT '简历 ID',
  `userId`       BIGINT       NOT NULL COMMENT '用户 ID',
  `targetRole`   VARCHAR(255)         DEFAULT NULL COMMENT '目标岗位',
  `targetLevel`  VARCHAR(255)         DEFAULT NULL COMMENT '目标级别',
  `analysisJson` LONGTEXT             DEFAULT NULL COMMENT '分析结果 JSON',
  `createTime`   DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`   DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`     TINYINT              DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_ara_userId` (`userId`),
  KEY `idx_ara_resumeId` (`resumeId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='基于简历的分析报告';

-- 面试会话
CREATE TABLE IF NOT EXISTS `ai_interview_session` (
  `id`         BIGINT       NOT NULL COMMENT '主键 ID',
  `userId`     BIGINT       NOT NULL COMMENT '用户 ID',
  `resumeId`   BIGINT       NOT NULL COMMENT '简历 ID',
  `type`       VARCHAR(50)          DEFAULT NULL COMMENT '会话类型：BEHAVIORAL/TECHNICAL/CODING/MIXED',
  `language`   VARCHAR(50)          DEFAULT NULL COMMENT '语言：zh-CN/en-US 等',
  `difficulty` VARCHAR(50)          DEFAULT NULL COMMENT '难度：JUNIOR/MID/SENIOR 等',
  `persona`    VARCHAR(100)         DEFAULT NULL COMMENT '面试官人格/风格标识',
  `configJson` LONGTEXT             DEFAULT NULL COMMENT '配置 JSON（是否开启编程题、实时提示等）',
  `status`     VARCHAR(50)          DEFAULT NULL COMMENT '状态：CREATED/RUNNING/FINISHED/CANCELLED',
  `startedAt`  DATETIME             DEFAULT NULL COMMENT '实际开始时间',
  `endedAt`    DATETIME             DEFAULT NULL COMMENT '实际结束时间',
  `createTime` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`   TINYINT              DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_ais_userId` (`userId`),
  KEY `idx_ais_resumeId` (`resumeId`),
  KEY `idx_ais_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 面试会话';

-- 面试题目
CREATE TABLE IF NOT EXISTS `ai_interview_question` (
  `id`           BIGINT       NOT NULL COMMENT '主键 ID',
  `sessionId`    BIGINT       NOT NULL COMMENT '会话 ID',
  `orderNo`      INT                   DEFAULT NULL COMMENT '题目在当前会话中的序号（从 1 开始）',
  `type`         VARCHAR(50)          DEFAULT NULL COMMENT '题目类型：BEHAVIORAL/TECHNICAL/CODING 等',
  `content`      LONGTEXT             DEFAULT NULL COMMENT '题目文本内容',
  `codingTaskId` VARCHAR(255)         DEFAULT NULL COMMENT '若为编程题，可关联题目 ID 或题目 JSON',
  `metadataJson` LONGTEXT             DEFAULT NULL COMMENT '扩展字段 JSON（样例输入输出、考察点等）',
  `createTime`   DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`   DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`     TINYINT              DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_aiq_sessionId` (`sessionId`),
  KEY `idx_aiq_orderNo` (`orderNo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 面试中产生的题目';

-- 面试回答
CREATE TABLE IF NOT EXISTS `ai_interview_answer` (
  `id`              BIGINT       NOT NULL COMMENT '主键 ID',
  `sessionId`       BIGINT       NOT NULL COMMENT '会话 ID',
  `questionId`      BIGINT       NOT NULL COMMENT '题目 ID',
  `userId`          BIGINT       NOT NULL COMMENT '用户 ID',
  `textAnswer`      LONGTEXT             DEFAULT NULL COMMENT '识别后的文本内容',
  `audioUrl`        VARCHAR(512)         DEFAULT NULL COMMENT '原始音频地址',
  `durationSeconds` INT                  DEFAULT NULL COMMENT '回答时长（秒）',
  `asrConfidence`   DOUBLE               DEFAULT NULL COMMENT 'ASR 置信度（0-1）',
  `evaluationJson`  LONGTEXT             DEFAULT NULL COMMENT '评分与即时反馈 JSON',
  `createTime`      DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime`      DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`        TINYINT              DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_aia_sessionId` (`sessionId`),
  KEY `idx_aia_questionId` (`questionId`),
  KEY `idx_aia_userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 面试中用户的回答';

-- 面试总评报告
CREATE TABLE IF NOT EXISTS `ai_interview_report` (
  `id`         BIGINT       NOT NULL COMMENT '主键 ID',
  `sessionId`  BIGINT       NOT NULL COMMENT '会话 ID',
  `userId`     BIGINT       NOT NULL COMMENT '用户 ID',
  `resumeId`   BIGINT       NOT NULL COMMENT '简历 ID',
  `reportJson` LONGTEXT             DEFAULT NULL COMMENT '报告内容 JSON（维度评分、题目摘要、整体建议等）',
  `createTime` DATETIME             DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete`   TINYINT              DEFAULT 0 COMMENT '逻辑删除标记 0=未删 1=已删',
  PRIMARY KEY (`id`),
  KEY `idx_air_sessionId` (`sessionId`),
  KEY `idx_air_userId` (`userId`),
  KEY `idx_air_resumeId` (`resumeId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='一次完整 AI 面试的总评报告';

