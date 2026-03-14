package com.digital.model.dto.learningpath;

import lombok.Data;

import java.io.Serializable;

/**
 * 学习路径主题重命名请求
 */
@Data
public class LearningPathRenameTopicRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 新的主题名称
     */
    private String topic;
}

