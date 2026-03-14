package com.digital.model.dto.learningpath;

import lombok.Data;

import java.io.Serializable;

/**
 * 学习路径 - 推荐知识点（供向 AI 提问）请求
 */
@Data
public class LearningPathRecommendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 知识主题，如「Vue」「Java 多线程」
     */
    private String topic;
}
