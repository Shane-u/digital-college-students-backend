package com.digital.model.dto.learningpath;

import java.io.Serializable;

/**
 * 孪孪伴学 - 保存学习路径请求
 * 用户确认后调用，将学习路径保存到 MySQL + Neo4j
 *
 * @author Shane
 */
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

    public String getPathJson() {
        return pathJson;
    }

    public void setPathJson(String pathJson) {
        this.pathJson = pathJson;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
