package com.digital.model.dto.learningpath;

import lombok.Data;

import java.io.Serializable;

/**
 * 学习路径节点点击 → 匹配闪卡图谱请求
 */
@Data
public class LearningPathFlashcardMatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 点击的学习路径节点 nodeId
     */
    private String clickedNodeId;

    /**
     * 最大后代节点数量（包含自身），用于保护性能
     */
    private Integer maxDescendants;

    /**
     * 最大关键词 token 数量（用于控制 Lucene Query 长度）
     */
    private Integer maxTokens;

    /**
     * Fulltext 返回 TopK
     */
    private Integer topK;

    /**
     * 固定阈值：score >= threshold 才点亮（可选）
     */
    private Double threshold;

    /**
     * 动态阈值：score >= maxScore * ratio 才点亮（可选，推荐 0.35）
     */
    private Double ratio;
}

