package com.digital.model.dto.learningpath;

import lombok.Data;

import java.io.Serializable;

/**
 * 孪孪伴学 - 保存学习路径请求
 * 用户确认后调用，将学习路径保存到 MySQL + Neo4j
 *
 * @author Shane
 */
@Data
public class LearningPathSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 学习路径 JSON（AI 生成的完整结果）
     */
    private String pathJson;

    /**
     * 路径主题，如 "Java 入门"
     */
    private String topic;

    /**
     * 路径描述（可选）
     */
    private String description;

    /**
     * 用户 ID（可选，若未登录可从 session 获取）
     */
    private Long userId;
}
